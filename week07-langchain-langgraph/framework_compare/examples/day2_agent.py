if __package__ in (None, ""):
    import _bootstrap

import json
from shipping.agent import ShippingAgent
from shipping.support import WorkflowModel


def show(label, result, events):
    print(f"\n[{label}]")
    print(json.dumps({**result, "tool_events": events}, ensure_ascii=False, indent=2))


def main():
    print("Day 2 / SCRIPTED_MODEL: 실제 에이전트·조회 도구와 모의 모델을 실행합니다.")
    agent = ShippingAgent(WorkflowModel())
    result = agent.start("multiple", "배송 상태와 도착 날짜가 궁금합니다.", ["O-100", "O-200"])
    show("복수 주문의 조회·결과 연결", result, agent.tool_events("multiple"))


if __name__ == "__main__":
    main()
