"""LangChain으로 모델·도구·업무 정책·요청별 상태를 구성하는 참고 구현."""
import json
from copy import deepcopy
from typing import Annotated

from langchain.agents import AgentState, create_agent
from langchain.agents.middleware import AgentMiddleware, ModelCallLimitMiddleware, hook_config
from langchain.tools import ToolRuntime, tool
from langchain_core.messages import AIMessage, RemoveMessage, ToolMessage
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph.message import REMOVE_ALL_MESSAGES
from langgraph.types import Command, Overwrite

from shipping.components import lookup_order
from shipping.support import (AGENT_POLICY, fixed_summary, initial_state, merge_orders,
                                    model_input, public_view, result_status)


class ShippingState(AgentState):
    issue: str
    order_ids: list[str]
    orders: Annotated[dict, merge_orders]
    attempts: int
    status: str
    answer: str


def shipping_tool(lookup=lookup_order):
    @tool
    def lookup_shipping(order_id: str, runtime: ToolRuntime) -> Command:
        """현재 문의의 주문 번호로 배송 사실을 조회합니다."""
        if order_id not in runtime.state["order_ids"]:
            return Command(update={"messages": [ToolMessage(
                content="현재 문의의 주문 번호만 조회할 수 있습니다.", status="error",
                tool_call_id=runtime.tool_call_id)]})
        facts = {order_id: lookup(order_id)}
        return Command(update={"orders": facts, "messages": [ToolMessage(
            content=json.dumps(facts, ensure_ascii=False), tool_call_id=runtime.tool_call_id)]})

    return lookup_shipping


class ShippingPolicy(AgentMiddleware):
    state_schema = ShippingState

    def __init__(self, max_empty_replies=2):
        if max_empty_replies < 1:
            raise ValueError("빈 보충 허용 횟수는 양수여야 합니다.")
        self.max_empty_replies = max_empty_replies

    @hook_config(can_jump_to=["end"])
    def before_model(self, state, runtime):
        if not state["order_ids"]:
            exhausted = state["attempts"] >= self.max_empty_replies
            answer = "주문 번호 확인이 필요합니다." if exhausted else "주문 번호를 알려 주세요."
            return {"status": "UNRESOLVED" if exhausted else "WAITING", "answer": answer,
                    "messages": [AIMessage(content=answer)], "jump_to": "end"}
        if set(state["orders"]) == set(state["order_ids"]) and result_status(state["orders"]) == "NOT_FOUND":
            answer = fixed_summary(state["orders"])
            return {"status": "NOT_FOUND", "answer": answer, "messages": [AIMessage(content=answer)],
                    "jump_to": "end"}
        return {"status": "READY", "answer": ""}

    def wrap_model_call(self, request, handler):
        response = handler(request)
        message = response.result[0]
        # 잘못된 응답은 모델 단계에서 실패시켜 재시도 때 모델을 다시 호출합니다.
        if not message.tool_calls and (not isinstance(message.content, str) or not message.content.strip()):
            raise ValueError("안내 문장이 필요합니다.")
        return response

    def after_model(self, state, runtime):
        response = state["messages"][-1]
        if response.tool_calls:
            return None
        if set(state["orders"]) != set(state["order_ids"]):
            return {"status": "INCOMPLETE", "answer": "필요한 조회가 끝나지 않아 답변을 보류했습니다."}
        return {"status": result_status(state["orders"]), "answer": response.content}


class ShippingAgent:
    """참고 도구·상태·정책을 재사용하고 문의의 시작·보충·결과 조회를 연결합니다."""

    def __init__(self, model, *, lookup=lookup_order,
                 max_empty_replies=2, max_model_calls=4):
        self.app = create_agent(
            model=model,
            tools=[shipping_tool(lookup)],
            system_prompt=AGENT_POLICY,
            state_schema=ShippingState,
            middleware=[
                ShippingPolicy(max_empty_replies=max_empty_replies),
                ModelCallLimitMiddleware(run_limit=max_model_calls, exit_behavior="error"),
            ],
            checkpointer=InMemorySaver(),
        )

    @staticmethod
    def config(request_id):
        if not isinstance(request_id, str) or not request_id.strip():
            raise ValueError("요청 ID는 비어 있지 않은 문자열이어야 합니다.")
        return {"configurable": {"thread_id": request_id}}

    def snapshot(self, request_id):
        return self.app.get_state(self.config(request_id))

    def view(self, request_id):
        snapshot = self.snapshot(request_id)
        if not snapshot.values:
            raise ValueError("시작하지 않은 요청입니다.")
        return deepcopy(public_view(snapshot))

    def start(self, request_id, issue, order_ids):
        config = self.config(request_id)
        if self.app.get_state(config).values:
            raise ValueError("이미 시작한 요청입니다.")
        state = initial_state(issue, order_ids)
        self.app.invoke({**state, "messages": [model_input(state)]}, config)
        return self.view(request_id)

    def _next_turn(self, request_id, order_ids, attempts):
        previous = self.snapshot(request_id).values
        state = initial_state(previous["issue"], order_ids)
        state["attempts"] = attempts
        # 병합 상태와 모델 문맥을 함께 교체해 이전 주문이 현재 근거로 남지 않게 합니다.
        self.app.invoke({
            **state,
            "orders": Overwrite({}),
            "messages": [RemoveMessage(id=REMOVE_ALL_MESSAGES), model_input(state)],
        }, self.config(request_id))
        return self.view(request_id)

    def supplement(self, request_id, order_ids):
        previous = self.snapshot(request_id)
        if previous.next or previous.values.get("status") != "WAITING":
            raise ValueError("보충 입력을 기다리는 요청이 아닙니다.")
        return self._next_turn(request_id, order_ids, previous.values["attempts"] + 1)

    def correct(self, request_id, order_ids):
        previous = self.snapshot(request_id)
        if not previous.values or previous.next or previous.values.get("status") == "WAITING":
            raise ValueError("처리가 끝난 요청의 번호를 정정하세요.")
        return self._next_turn(request_id, order_ids, 0)

    def tool_events(self, request_id):
        """현재 턴의 요청 ID와 실제 도구 결과를 기존 메시지에서 읽습니다."""
        snapshot = self.snapshot(request_id)
        if not snapshot.values:
            raise ValueError("시작하지 않은 요청입니다.")
        events = []
        for message in snapshot.values["messages"]:
            if isinstance(message, AIMessage) and message.tool_calls:
                events.append({"requests": [{"id": call["id"], "tool": call["name"],
                                              "args": call["args"]}
                                             for call in message.tool_calls]})
            elif isinstance(message, ToolMessage):
                events.append({"result_for": message.tool_call_id,
                               "status": message.status, "content": message.content})
        return events

    def retry(self, request_id):
        if not self.snapshot(request_id).next:
            raise ValueError("재시도할 실행 단계가 없습니다.")
        self.app.invoke(None, self.config(request_id))
        return self.view(request_id)
