"""LangGraph로 같은 배송 문의의 입력·조회·생성·대기·종료 경로를 구성하는 참고 구현."""
from typing import TypedDict
from copy import deepcopy

from langchain_core.messages import SystemMessage
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph import END, START, StateGraph
from langgraph.types import Command, interrupt

from shipping.components import lookup_order
from shipping.support import (ANSWER_POLICY, fixed_summary, initial_state, model_input,
                                    normalize_ids, public_view, result_status)


class ShippingState(TypedDict):
    issue: str
    order_ids: list[str]
    orders: dict
    attempts: int
    status: str
    answer: str


def build_shipping_graph(model, *, lookup=lookup_order, max_empty_replies=2):
    if max_empty_replies < 1:
        raise ValueError("빈 보충 허용 횟수는 양수여야 합니다.")

    def prepare(state):
        return {"status": "READY" if state["order_ids"] else "WAITING"}

    def collect(state):
        supplied = interrupt({"question": "주문 번호를 알려 주세요.", "issue": state["issue"]})
        ids = normalize_ids(supplied)
        attempts = state["attempts"] + 1
        status = "READY" if ids else ("WAITING" if attempts < max_empty_replies else "UNRESOLVED")
        return {"order_ids": ids, "attempts": attempts, "status": status,
                "answer": "주문 번호 확인이 필요합니다." if status == "UNRESOLVED" else ""}

    def fetch(state):
        orders = {value: lookup(value) for value in state["order_ids"]}
        return {"orders": orders, "status": "READY"}

    def draft(state):
        response = model.invoke([SystemMessage(content=ANSWER_POLICY), model_input(state, with_facts=True)])
        if not isinstance(response.content, str) or not response.content.strip():
            raise ValueError("안내 문장이 필요합니다.")
        return {"answer": response.content, "status": result_status(state["orders"])}

    def unavailable(state):
        return {"status": "NOT_FOUND", "answer": fixed_summary(state["orders"])}

    graph = StateGraph(ShippingState)
    for name, action in (("prepare", prepare), ("collect", collect), ("lookup", fetch),
                         ("draft", draft), ("unavailable", unavailable)):
        graph.add_node(name, action)
    graph.add_edge(START, "prepare")
    graph.add_conditional_edges("prepare", lambda state: "lookup" if state["order_ids"] else "collect")
    graph.add_conditional_edges("collect", lambda state: {"READY": "lookup", "WAITING": "collect",
                                                         "UNRESOLVED": END}[state["status"]])
    graph.add_conditional_edges("lookup", lambda state: "unavailable" if result_status(state["orders"]) == "NOT_FOUND" else "draft")
    graph.add_edge("draft", END)
    graph.add_edge("unavailable", END)
    return graph.compile(checkpointer=InMemorySaver())


class ShippingGraph:
    """업무 노드·간선은 build_shipping_graph, 요청 접점은 이 클래스가 맡습니다."""

    def __init__(self, model, *, lookup=lookup_order, max_empty_replies=2):
        self.app = build_shipping_graph(
            model, lookup=lookup, max_empty_replies=max_empty_replies
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
        result = deepcopy(public_view(snapshot))
        questions = [
            item.value for task in snapshot.tasks for item in task.interrupts
        ]
        result["question"] = questions[0] if questions else None
        if result["question"] is not None:
            result["answer"] = result["question"]["question"]
        return result

    def start(self, request_id, issue, order_ids):
        config = self.config(request_id)
        if self.app.get_state(config).values:
            raise ValueError("이미 시작한 요청입니다.")
        self.app.invoke(initial_state(issue, order_ids), config)
        return self.view(request_id)

    def supplement(self, request_id, order_ids):
        snapshot = self.snapshot(request_id)
        if not any(task.interrupts for task in snapshot.tasks):
            raise ValueError("보충 입력을 기다리는 요청이 아닙니다.")
        # 원래 문의를 새 입력으로 만들지 않고, 중단된 collect에 번호를 반환합니다.
        self.app.invoke(
            Command(resume=normalize_ids(order_ids)), self.config(request_id)
        )
        return self.view(request_id)

    def correct(self, request_id, order_ids):
        previous = self.snapshot(request_id)
        if not previous.values or previous.next or previous.values.get("status") == "WAITING":
            raise ValueError("처리가 끝난 요청의 번호를 정정하세요.")
        # 완료 요청은 START부터 새 번호로 계산합니다. 대기 입력의 resume과 구분합니다.
        self.app.invoke(
            initial_state(previous.values["issue"], order_ids), self.config(request_id)
        )
        return self.view(request_id)

    def retry(self, request_id):
        snapshot = self.snapshot(request_id)
        if not snapshot.next or any(task.interrupts for task in snapshot.tasks):
            raise ValueError("재시도할 실패 단계가 없습니다.")
        # interrupt 보충과 달리 새 입력 없이 실패한 실행을 이어갑니다.
        self.app.invoke(None, self.config(request_id))
        return self.view(request_id)
