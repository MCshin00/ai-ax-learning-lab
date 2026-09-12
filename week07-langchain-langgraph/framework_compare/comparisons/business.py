"""제공 업무 함수. 세 연결 방식이 같은 입력·조회·생성 경계를 사용합니다."""
from typing import TypedDict


class State(TypedDict, total=False):
    order_id: str
    issue: str
    order: dict | None
    status: str
    answer: str
    path: list[str]
    generation: str


from shipping.components import ORDERS


def prepare(state: State) -> State:
    # 현재 요청의 파생값을 초기화합니다. 과거 문의 사실을 새 주문에 붙이지 않습니다.
    order_id = state.get("order_id", "")
    issue = state.get("issue", "배송 상태를 알려 주세요.")
    if not isinstance(order_id, str) or not isinstance(issue, str):
        raise ValueError("order_id와 issue는 문자열이어야 합니다.")
    return {"order_id": order_id.strip(), "issue": issue, "order": None,
            "status": "RECEIVED", "answer": "", "path": ["prepare"], "generation": "NONE"}


def ask(state: State) -> State:
    return {**state, "status": "NEEDS_INPUT", "answer": "주문 번호를 알려 주세요.",
            "path": [*state["path"], "ask"]}


def lookup(state: State) -> State:
    order = ORDERS.get(state["order_id"])
    result = None if order is None else {"order_id": state["order_id"], **order}
    return {**state, "order": result, "status": "FOUND" if result else "NOT_FOUND",
            "path": [*state["path"], "lookup"]}


def unavailable(state: State) -> State:
    return {**state, "answer": "해당 주문을 찾지 못했습니다.",
            "path": [*state["path"], "unavailable"]}


def fixed_answer(state: State) -> str:
    order = state["order"]
    return f"{order['order_id']}: {order['shipping']}. 문의 내용은 추가 사실로 확정하지 않았습니다."


def make_draft(generator=fixed_answer, generation="FIXED_OFFLINE"):
    def draft(state: State) -> State:
        answer = generator(state)
        if not isinstance(answer, str) or not answer.strip():
            raise ValueError("생성기가 빈 답변을 반환했습니다.")
        return {**state, "status": "ANSWERED", "answer": answer,
                "path": [*state["path"], "draft"], "generation": generation}
    return draft


def after_prepare(state: State) -> str:
    return "lookup" if state["order_id"] else "ask"


def after_lookup(state: State) -> str:
    return "draft" if state["status"] == "FOUND" else "unavailable"
