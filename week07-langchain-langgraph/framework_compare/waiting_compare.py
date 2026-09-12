"""같은 두 배송 문의를 앱의 세션과 LangGraph 체크포인트로 보관·재개합니다."""
import argparse
import json
from copy import deepcopy

from langgraph.types import Command

from clarification import build_waiting_flow
from components import fixed_reply, live_reply, lookup_order


class DirectWaitingFlow:
    def __init__(self, answerer=fixed_reply):
        self.sessions = {}
        self.answerer = answerer

    def start(self, request_id, issue):
        if request_id in self.sessions:
            raise ValueError("이미 시작한 요청입니다.")
        self.sessions[request_id] = {"issue": issue, "order_id": "", "attempts": 0,
                                     "status": "WAITING", "answer": "", "next": ["collect"]}
        return self.snapshot(request_id)

    def resume(self, request_id, supplied):
        state = self.sessions[request_id]
        if state["next"] != ["collect"]:
            raise ValueError("보충 입력을 기다리는 요청이 아닙니다.")
        state["attempts"] += 1
        if not isinstance(supplied, str) or not supplied.strip():
            state["status"] = "WAITING" if state["attempts"] < 2 else "UNRESOLVED"
            state["answer"] = "주문 번호 확인이 필요합니다."
            state["next"] = ["collect"] if state["status"] == "WAITING" else []
            return self.snapshot(request_id)
        state["order_id"] = supplied.strip()
        state.update(status="READY", answer="", next=["answer"])
        facts = lookup_order(state["order_id"])
        if facts is None:
            state.update(status="NOT_FOUND", answer="해당 주문을 찾지 못했습니다.")
        else:
            # 완료 결과가 준비된 뒤 요청을 종료합니다.
            answer = self.answerer(state["issue"], facts)
            state.update(status="ANSWERED", answer=answer)
        state["next"] = []
        return self.snapshot(request_id)

    def snapshot(self, request_id):
        result = deepcopy(self.sessions[request_id])
        result["question"] = ({"question": "주문 번호를 알려 주세요.", "issue": result["issue"]}
                              if result["next"] == ["collect"] else None)
        return result


class GraphWaitingFlow:
    def __init__(self, answerer=fixed_reply):
        self.app = build_waiting_flow(answerer)

    @staticmethod
    def config(request_id):
        return {"configurable": {"thread_id": request_id}}

    def start(self, request_id, issue):
        config = self.config(request_id)
        if self.app.get_state(config).values:
            raise ValueError("이미 시작한 요청입니다.")
        self.app.invoke({"issue": issue, "order_id": "", "attempts": 0,
                         "status": "WAITING", "answer": ""}, config)
        return self.snapshot(request_id)

    def resume(self, request_id, supplied):
        config = self.config(request_id)
        snapshot = self.app.get_state(config)
        if not any(task.interrupts for task in snapshot.tasks):
            raise ValueError("보충 입력을 기다리는 요청이 아닙니다.")
        self.app.invoke(Command(resume=supplied), config)
        return self.snapshot(request_id)

    def snapshot(self, request_id):
        snapshot = self.app.get_state(self.config(request_id))
        questions = [item.value for task in snapshot.tasks for item in task.interrupts]
        return {**snapshot.values, "next": list(snapshot.next),
                "question": questions[0] if questions else None}


WAITING_BUILDERS = {"direct": DirectWaitingFlow, "langgraph": GraphWaitingFlow}


def compare_waiting(methods=None):
    for method, builder in (WAITING_BUILDERS if methods is None else methods).items():
        flow = builder()
        for request_id, issue in (("request-A", "배송 예정일 문의"), ("request-B", "현재 배송 상태 문의")):
            yield {"method": method, "request_id": request_id, "phase": "waiting",
                   **flow.start(request_id, issue)}
        for request_id, supplied in (("request-B", "O-200"), ("request-A", "O-100")):
            yield {"method": method, "request_id": request_id, "phase": "resumed",
                   **flow.resume(request_id, supplied)}


def single_request(flow):
    yield {"phase": "waiting", **flow.start("provided-live-request", "배송 예정일 문의")}
    yield {"phase": "resumed", **flow.resume("provided-live-request", "O-100")}


def main():
    parser = argparse.ArgumentParser(description="문의 보관과 재개의 책임 비교")
    parser.add_argument("--method", choices=[*WAITING_BUILDERS, "all"], default="all")
    parser.add_argument("--live", action="store_true")
    args = parser.parse_args()
    if args.live and args.method == "all":
        parser.error("실제 모델은 --method로 한 방식을 지정하세요.")
    selected = WAITING_BUILDERS if args.method == "all" else {args.method: WAITING_BUILDERS[args.method]}
    if args.live:
        flow = selected[args.method](live_reply())
        events = single_request(flow)
    else:
        events = compare_waiting(selected)
    for event in events:
        event = {"mode": "LIVE_MODEL" if args.live else "FIXED_OFFLINE", "method": args.method, **event}
        print(json.dumps(event, ensure_ascii=False))


if __name__ == "__main__":
    main()
