"""Day 3: 조회 뒤 생성만 실패하면 저장된 사실로 생성부터 재시도합니다."""

if __package__ in (None, ""):
    import _bootstrap

from shipping.components import lookup_order
from shipping.graph import ShippingGraph
from examples.day3_graph import show
from shipping.support import WorkflowModel


def main():
    print("Day 3 / SCRIPTED_MODEL: 안내 생성에서 한 번 실패하도록 만든 사례입니다.")
    lookup_calls = []

    def lookup(order_id):
        lookup_calls.append(order_id)
        return lookup_order(order_id)

    graph = ShippingGraph(WorkflowModel(fail_draft_once=True), lookup=lookup)
    try:
        graph.start("A", "배송 문의", ["O-100"])
    except RuntimeError as error:
        if str(error) != "MODEL_UNAVAILABLE":
            raise
        show("생성 실패: 조회 사실과 다음 위치", {
            "phase": "generation_failed", "error": str(error),
            **graph.view("A"), "lookup_calls": list(lookup_calls)
        })
    else:
        raise AssertionError("제공 모의 모델의 생성 실패가 발생하지 않았습니다.")
    show("생성 재시도 후", {
        "phase": "retried", **graph.retry("A"),
        "lookup_calls": list(lookup_calls)
    })


if __name__ == "__main__":
    main()