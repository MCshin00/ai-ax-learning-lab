"""Day 3: 같은 그래프의 두 문의를 역순으로 재개합니다."""

if __package__ in (None, ""):
    import _bootstrap

import argparse

from shipping.graph import ShippingGraph
from examples.day3_graph import show
from shipping.support import WorkflowModel


def main():
    parser = argparse.ArgumentParser(description="요청별 중단·재개 또는 빈 보충 종료")
    parser.add_argument("--empty", action="store_true", help="빈 보충 종료 사례만 실행")
    args = parser.parse_args()
    graph = ShippingGraph(WorkflowModel())
    print("Day 3 / SCRIPTED_MODEL: 저장된 문의와 중단 위치에 번호를 보충합니다.")
    if args.empty:
        show("번호 없이 시작", graph.start("A", "배송 문의", []))
        show("첫 번째 빈 보충", graph.supplement("A", []))
        show("두 번째 빈 보충", graph.supplement("A", []))
        return
    for request_id, issue in (("A", "배송 예정일 문의"), ("B", "현재 배송 상태 문의")):
        show(f"{request_id}: 번호 대기", {
            "phase": "waiting", "request_id": request_id,
            **graph.start(request_id, issue, [])
        })
    for request_id, ids in (("B", ["O-200"]), ("A", ["O-100"])):
        show(f"{request_id}: 번호 보충 후 재개", {
            "phase": "resumed", "request_id": request_id,
            **graph.supplement(request_id, ids)
        })


if __name__ == "__main__":
    main()