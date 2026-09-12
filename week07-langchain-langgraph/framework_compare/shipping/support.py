"""두 참고 구성에서 공유하는 제공 입력·업무 자료와 모의 모델. 실행 제어는 각 구성에 둡니다."""
import json
import re

from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from langchain_core.outputs import ChatGeneration, ChatResult


ANSWER_POLICY = "조회한 배송 사실로만 답하세요. 자료에 없는 도착 날짜·결제·환불 사실을 확정하지 마세요."
AGENT_POLICY = (ANSWER_POLICY + " 입력의 order_ids 각각을 lookup_shipping으로 조회하세요. "
                "원래 문의는 issue입니다. 현재 입력에 없는 주문을 조회하지 마세요.")


def normalize_ids(values):
    if not isinstance(values, list) or not all(isinstance(value, str) for value in values):
        raise ValueError("주문 번호 입력은 문자열 목록이어야 합니다.")
    ids = list(dict.fromkeys(value.strip() for value in values if value.strip()))
    if any(re.fullmatch(r"O-\d+", value) is None for value in ids):
        raise ValueError("제공 주문 번호 형식은 O-100과 같습니다.")
    return ids


def initial_state(issue, order_ids):
    if not isinstance(issue, str) or not issue.strip():
        raise ValueError("원래 문의를 입력하세요.")
    return {"issue": issue, "order_ids": normalize_ids(order_ids), "orders": {},
            "attempts": 0, "status": "RECEIVED", "answer": ""}


def merge_orders(previous, updates):
    return {**previous, **updates}


def result_status(orders):
    found = sum(value is not None for value in orders.values())
    return "NOT_FOUND" if found == 0 else ("ANSWERED" if found == len(orders) else "PARTIAL")


def fixed_summary(orders):
    return " / ".join(f"{key}: 해당 주문을 찾지 못했습니다." if facts is None
                      else f"{key}: {facts['shipping']}" for key, facts in orders.items())


def model_input(state, *, with_facts=False):
    payload = {"issue": state["issue"], "order_ids": state["order_ids"]}
    if with_facts:
        payload["orders"] = state["orders"]
    return HumanMessage(content=json.dumps(payload, ensure_ascii=False))


def public_view(snapshot):
    state = snapshot.values
    return {key: state.get(key) for key in ("issue", "order_ids", "orders", "attempts", "status", "answer")} | {
        "next": list(snapshot.next),
        "interrupted": any(task.interrupts for task in snapshot.tasks),
    }


class WorkflowModel(BaseChatModel):
    """구조화된 제공 입력으로 정해진 호출을 생성합니다. 자연어 이해·답변 품질의 검증은 아닙니다."""
    fail_draft_once: bool = False

    @property
    def _llm_type(self):
        return "scripted-order-workflow"

    def bind_tools(self, tools, **kwargs):
        return self

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        start = max(i for i, message in enumerate(messages) if isinstance(message, HumanMessage))
        payload = json.loads(messages[start].content)
        orders = payload.get("orders")
        tool_results = [message for message in messages[start:] if isinstance(message, ToolMessage)]
        if orders is None and tool_results:
            orders = {}
            for result in tool_results:
                if result.status == "success":
                    orders.update(json.loads(result.content))
        if orders is not None:
            if self.fail_draft_once:
                self.fail_draft_once = False
                raise RuntimeError("MODEL_UNAVAILABLE")
            answer = fixed_summary(orders) if orders else "조회 요청을 완료하지 못했습니다."
            response = AIMessage(content=answer)
        else:
            response = AIMessage(content="", tool_calls=[
                {"name": "lookup_shipping", "args": {"order_id": value},
                 "id": f"shipping-{i}", "type": "tool_call"}
                for i, value in enumerate(payload["order_ids"])
            ])
        return ChatResult(generations=[ChatGeneration(message=response)])
