"""Day 2: 같은 ShippingAgent에 두 문의를 보관하고 번호만 역순으로 보충합니다."""

if __package__ in (None, ""):
    import _bootstrap

import json

from shipping.agent import ShippingAgent
from shipping.support import WorkflowModel


def main():
    print("Day 2 / SCRIPTED_MODEL: 요청별 문의 보관과 번호 보충")
    agent = ShippingAgent(WorkflowModel())
    for request_id, issue in (("A", "배송 예정일 문의"), ("B", "현재 배송 상태 문의")):
        result = agent.start(request_id, issue, [])
        print(json.dumps({"phase": "waiting", "request_id": request_id, **result},
                         ensure_ascii=False, indent=2))
    for request_id, order_ids in (("B", ["O-200"]), ("A", ["O-100"])):
        result = agent.supplement(request_id, order_ids)
        print(json.dumps({"phase": "supplemented", "request_id": request_id, **result},
                         ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
