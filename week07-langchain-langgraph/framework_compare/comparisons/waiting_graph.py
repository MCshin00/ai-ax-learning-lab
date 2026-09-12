"""대기 요구가 있을 때의 비교 예제. InMemorySaver는 프로세스 재시작 후 복원을 제공하지 않습니다."""
from typing import TypedDict

from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph import END, START, StateGraph
from langgraph.types import interrupt
from shipping.components import fixed_reply, lookup_order


class PendingRequest(TypedDict, total=False):
    issue: str
    order_id: str
    attempts: int
    status: str
    answer: str


def build_waiting_flow(answerer=fixed_reply):
    def collect(state):
        if state.get("order_id", "").strip():
            return {"status": "READY", "answer": ""}
        # 재개하면 이 노드는 처음부터 실행됩니다. interrupt 앞에 외부 작업을 두지 않습니다.
        supplied = interrupt({"question": "주문 번호를 알려 주세요.", "issue": state["issue"]})
        attempts = state.get("attempts", 0) + 1
        if isinstance(supplied, str) and supplied.strip():
            return {"order_id": supplied.strip(), "attempts": attempts, "status": "READY", "answer": ""}
        return {"attempts": attempts, "status": "WAITING" if attempts < 2 else "UNRESOLVED",
                "answer": "주문 번호 확인이 필요합니다."}

    def route(state):
        return "answer" if state["status"] == "READY" else ("collect" if state["status"] == "WAITING" else END)

    def answer(state):
        facts = lookup_order(state["order_id"])
        if facts is None:
            return {"status": "NOT_FOUND", "answer": "해당 주문을 찾지 못했습니다."}
        return {"status": "ANSWERED", "answer": answerer(state["issue"], facts)}

    graph = StateGraph(PendingRequest)
    graph.add_node("collect", collect)
    graph.add_node("answer", answer)
    graph.add_edge(START, "collect")
    graph.add_conditional_edges("collect", route, {"answer": "answer", "collect": "collect", END: END})
    graph.add_edge("answer", END)
    return graph.compile(checkpointer=InMemorySaver())
