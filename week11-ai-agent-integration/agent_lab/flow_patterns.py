"""처리 구조를 읽는 작은 비교 예. 앱 내부 다중 에이전트를 구현한 예제는 아닙니다."""
from concurrent.futures import ThreadPoolExecutor
import json
from business import OrderLookup, load_orders


def route(kind, order_id, lookup, search):
    if kind == "status":
        return {"order": lookup(order_id)}
    if kind == "cancellation":
        from pipeline_example import cancellation_pipeline
        return cancellation_pipeline(order_id, lookup, search)
    return {"status": "needs_clarification"}


def collect_orders(order_ids, lookup):
    """독립 조회는 병렬화해도 각 결과와 실패를 원래 주문 번호에 연결합니다."""
    def one(order_id):
        try:
            return lookup(order_id)
        except (TimeoutError, ConnectionError):
            return {"status": "unavailable", "order_id": order_id}
    ids = list(dict.fromkeys(order_ids))
    with ThreadPoolExecutor(max_workers=2) as pool:
        return dict(zip(ids, pool.map(one, ids)))


def revise_once(draft, inspect, revise):
    """수정 조건과 상한이 있는 검토. inspect가 놓친 오류까지 찾아낸다고 보장하지 않습니다."""
    feedback = inspect(draft)
    if not feedback:
        return {"status": "accepted", "draft": draft, "revisions": 0}
    corrected = revise(draft, feedback)
    remaining = inspect(corrected)
    return {"status": "needs_review" if remaining else "accepted", "draft": corrected,
            "feedback": remaining, "revisions": 1}


def main():
    lookup = OrderLookup(load_orders())
    search = lambda query: {"status": "no_evidence", "matches": []}
    print(json.dumps({"mode": "LOCAL_STRUCTURE_EXAMPLE",
        "routing": route("status", "A-102", lookup, search),
        "independent": collect_orders(["A-102", "A-999", "A-103"], lookup),
        "revision": revise_once({"answer": "취소 신청 조건", "sources": []},
            lambda draft: [] if draft["sources"] else ["출처가 없음"],
            lambda draft, feedback: draft)}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
