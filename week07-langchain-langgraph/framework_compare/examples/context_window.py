"""보관 기록과 실제 모델 입력을 비교합니다. --live는 학습자가 IDE에서 실행합니다."""
import _bootstrap
import argparse
import json
from shipping.agent import ShippingAgent
from shipping.support import WorkflowModel


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--live", action="store_true")
    args = parser.parse_args()
    if args.live:
        from shipping.model_boundary import live_model
        model = live_model()
    else:
        model = WorkflowModel()
    app = ShippingAgent(model, retain_history=True)
    app.start("context-demo", "배송 상태를 알고 싶습니다.", [])
    app.supplement("context-demo", ["O-100"])
    result = app.correct("context-demo", ["O-200"])
    print(json.dumps({"mode": "LIVE" if args.live else "SCRIPTED_OFFLINE", "result": result,
        "stored_messages": [message.content for message in app.snapshot("context-demo").values["messages"]],
        "model_messages": [message.content for message in app.policy.last_model_messages]}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
