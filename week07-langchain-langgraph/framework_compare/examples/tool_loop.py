"""같은 모델·조회 도구로 직접 실행 루프와 LangChain create_agent를 비교합니다."""

if __package__ in (None, ""):
    import _bootstrap

import argparse
import json
import re

from langchain.agents import create_agent
from langchain.agents.middleware import ModelCallLimitMiddleware
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_core.outputs import ChatGeneration, ChatResult
from langchain_core.tools import StructuredTool
from pydantic import ValidationError

from shipping.components import lookup_order


POLICY = ("제공된 배송 문의를 처리하세요. 주문 번호가 없으면 번호를 질문하세요. "
          "번호가 있으면 lookup_order로 각각 조회하고, 반환된 사실로만 답하세요. "
          "없는 주문은 찾지 못했다고 알리고 결제·환불 사실을 추측하지 마세요.")
CASES = ("O-100 배송 상태를 알려 주세요.", "O-100과 O-200 배송 상태를 비교해 주세요.",
         "배송 상태를 알려 주세요.", "O-999 배송 상태를 알려 주세요.")


class ScriptedOrderModel(BaseChatModel):
    """제공 번호를 읽어 정해진 도구 요청을 만드는 모형. 실제 모델 판단을 검증하지 않습니다."""

    @property
    def _llm_type(self):
        return "scripted-order-model"

    def bind_tools(self, tools, **kwargs):
        return self

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        user_index = max(i for i, message in enumerate(messages) if isinstance(message, HumanMessage))
        current = messages[user_index:]
        results = [message for message in current if isinstance(message, ToolMessage)]
        if results:
            parts = []
            for result in results:
                if result.status == "error":
                    parts.append("조회 요청을 처리하지 못했습니다.")
                    continue
                facts = json.loads(result.content)
                parts.append("해당 주문을 찾지 못했습니다." if facts is None
                             else f"{facts['order_id']}: {facts['shipping']}")
            response = AIMessage(content=" / ".join(parts))
        else:
            ids = list(dict.fromkeys(re.findall(r"O-\d+", str(current[0].content))))
            response = AIMessage(content="" if ids else "주문 번호를 알려 주세요.", tool_calls=[
                {"name": "lookup_order", "args": {"order_id": order_id},
                 "id": f"lookup-{index}", "type": "tool_call"}
                for index, order_id in enumerate(ids)
            ])
        return ChatResult(generations=[ChatGeneration(message=response)])


def order_tool():
    return StructuredTool.from_function(lookup_order, description="주문 번호로 제공 배송 사실을 조회합니다.")


def build_direct_agent(model, tools=None, max_model_calls=4):
    available = [order_tool()] if tools is None else tools
    registry = {tool.name: tool for tool in available}
    bound = model.bind_tools(available)

    def run(issue):
        messages = [SystemMessage(content=POLICY), HumanMessage(content=issue)]
        for _ in range(max_model_calls):
            response = bound.invoke(messages)
            messages.append(response)
            if not response.tool_calls:
                return {"messages": messages[1:]}
            for call in response.tool_calls:
                if call["name"] not in registry:
                    result = ToolMessage(content="등록되지 않은 도구입니다.",
                                         tool_call_id=call["id"], status="error")
                else:
                    try:
                        result = registry[call["name"]].invoke(call)
                    except ValidationError:
                        result = ToolMessage(content="도구 입력 형식을 확인하세요.",
                                             tool_call_id=call["id"], status="error")
                messages.append(result)
        raise RuntimeError("모델 호출 한도에 도달했습니다.")

    return run


def build_langchain_agent(model, tools=None, max_model_calls=4):
    available = [order_tool()] if tools is None else tools
    agent = create_agent(model=model, tools=available, system_prompt=POLICY,
                         middleware=[ModelCallLimitMiddleware(run_limit=max_model_calls,
                                                              exit_behavior="error")])
    return lambda issue: agent.invoke({"messages": [HumanMessage(content=issue)]})


AGENT_BUILDERS = {"direct": build_direct_agent, "langchain": build_langchain_agent}


def summarize(result):
    # 메서드 간 비교에는 도구 요청·결과의 연결과 최종 답변만 사용합니다.
    messages = result["messages"]
    events = []
    for message in messages:
        if isinstance(message, AIMessage):
            events.append({"model_requests": [{"name": call["name"], "args": call["args"],
                                                "id": call["id"]} for call in message.tool_calls]})
        elif isinstance(message, ToolMessage):
            events.append({"tool_result_for": message.tool_call_id,
                           "result": message.content, "status": message.status})
    return {"events": events, "answer": messages[-1].content}


def main():
    parser = argparse.ArgumentParser(description="모델–도구 실행 루프의 책임 비교")
    parser.add_argument("--method", choices=[*AGENT_BUILDERS, "all"], default="all")
    parser.add_argument("--issue", help="생략하면 제공 사례를 모두 실행합니다.")
    args = parser.parse_args()
    selected = AGENT_BUILDERS if args.method == "all" else {args.method: AGENT_BUILDERS[args.method]}
    for issue in CASES if args.issue is None else [args.issue]:
        for method, builder in selected.items():
            model = ScriptedOrderModel()
            result = builder(model)(issue)
            print(json.dumps({"method": method, "mode": "SCRIPTED_MODEL",
                              "issue": issue, **summarize(result)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
