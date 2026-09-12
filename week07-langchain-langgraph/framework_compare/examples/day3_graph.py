if __package__ in (None, ""):
    import _bootstrap

import json
from shipping.graph import ShippingGraph
from shipping.support import WorkflowModel


def show(label, result):
    print(f"\n[{label}]")
    print(json.dumps(result, ensure_ascii=False, indent=2))


def main():
    print("Day 3 / SCRIPTED_MODEL: 코드가 조회하고 모의 모델이 안내를 생성합니다.")
    graph = ShippingGraph(WorkflowModel())
    show("복수 주문의 조회·안내", graph.start(
        "multiple", "배송 상태와 도착 날짜가 궁금합니다.", ["O-100", "O-200"]
    ))


if __name__ == "__main__":
    main()