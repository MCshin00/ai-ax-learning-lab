"""대기 요구가 있을 때의 비교 예제. InMemorySaver는 프로세스 재시작 후 복원을 제공하지 않습니다."""
import json
from typing import TypedDict

from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph import END, START, StateGraph
from langgraph.types import Command, interrupt
from components import fixed_reply, lookup_order


class PendingRequest(TypedDict, total=False):
    issue: str
    order_id: str
    attempts: int
    status: str
    answer: str


def build_waiting_flow(answerer=fixed_reply):
    def collect(state):
        if state.get("order_id", "").strip():
            return {"status": "READY"}
        # 재개하면 이 노드는 처음부터 실행됩니다. interrupt 앞에 외부 작업을 두지 않습니다.
        supplied = interrupt({"question": "주문 번호를 알려 주세요.", "issue": state["issue"]})
        attempts = state.get("attempts", 0) + 1
        if isinstance(supplied, str) and supplied.strip():
            return {"order_id": supplied.strip(), "attempts": attempts, "status": "READY"}
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


if __name__ == "__main__":
    app = build_waiting_flow()
    config = {"configurable": {"thread_id": "provided-order-request"}}
    first = app.invoke({"issue": "배송 문의", "order_id": "", "attempts": 0}, config)
    print(json.dumps({"phase": "waiting", "question": first["__interrupt__"][0].value}, ensure_ascii=False))
    # 제공 입력으로 SDK 재개를 시연합니다. 실제 사용자의 응답을 받았다는 기록은 아닙니다.
    final = app.invoke(Command(resume="O-100"), config)
    print(json.dumps({"phase": "resumed", **final}, ensure_ascii=False))
