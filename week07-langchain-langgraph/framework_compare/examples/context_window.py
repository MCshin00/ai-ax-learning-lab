"""보관 기록과 호출별 모델 입력을 비교합니다. --live는 IDE에서 실행합니다."""
if __package__ in (None, ""):
    import _bootstrap

import argparse
from copy import deepcopy
from datetime import datetime
import json
from pathlib import Path

from langchain_core.callbacks import BaseCallbackHandler
from langchain_core.messages import AIMessage, ToolMessage

from shipping.agent import ShippingAgent
from shipping.support import WorkflowModel


OUTPUT_DIR = Path(__file__).resolve().parents[1] / ".local"


def message_view(message):
    """문의·호출·결과를 보여 주며 SDK의 인증·응답 메타데이터는 출력하지 않습니다."""
    value = {"type": message.type, "content": deepcopy(message.content)}
    if isinstance(message, AIMessage) and message.tool_calls:
        value["tool_calls"] = [
            {"id": call["id"], "name": call["name"], "args": deepcopy(call["args"])}
            for call in message.tool_calls
        ]
    if isinstance(message, ToolMessage):
        value.update(tool_call_id=message.tool_call_id, status=message.status)
    return value


class ModelInputs(BaseCallbackHandler):
    """선별 후 모델에 전달되는 메시지를 제공 사례의 각 단계에서 확인합니다."""

    def __init__(self):
        self.calls = []

    def on_chat_model_start(self, serialized, messages, **kwargs):
        for batch in messages:
            self.calls.append([message_view(message) for message in batch])


def run_demo(model):
    recorder = ModelInputs()
    previous_callbacks = model.callbacks
    model.callbacks = [*(previous_callbacks or []), recorder]
    try:
        app = ShippingAgent(model, retain_history=True)
        reports = []

        def observe(step, request_id, operation):
            # 모델을 부르지 않는 대기 단계에 직전의 다른 요청을 표시하지 않도록 구분합니다.
            recorder.calls.clear()
            result = operation()
            requests = deepcopy(recorder.calls)
            report = {
                "step": step,
                "request_id": request_id,
                "result": result,
                "stored_messages": [message_view(message) for message in
                                    app.snapshot(request_id).values["messages"]],
                "model_requests": requests,
                "model_messages": requests[-1] if requests else [],
                "tool_events": app.tool_events(request_id),
            }
            reports.append(report)

        observe("A_WAITING", "A", lambda: app.start("A", "배송 상태를 알고 싶습니다.", []))
        observe("A_SUPPLEMENT_O100", "A", lambda: app.supplement("A", ["O-100"]))
        observe("A_CORRECT_O200", "A", lambda: app.correct("A", ["O-200"]))
        observe("B_WAITING", "B", lambda: app.start("B", "별도 주문의 도착 날짜가 궁금합니다.", []))
        observe("B_SUPPLEMENT_O100", "B", lambda: app.supplement("B", ["O-100"]))
        return reports
    finally:
        model.callbacks = previous_callbacks


def save_report(report):
    """전체 메시지를 UTF-8 JSON으로 보관하고 실행마다 다른 파일을 만듭니다."""
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    path = OUTPUT_DIR / f"context-window-{report['mode'].lower()}-{report['provider']}-{stamp}.json"
    with path.open("x", encoding="utf-8") as output:
        json.dump(report, output, ensure_ascii=False, indent=2)
        output.write("\n")
    return path


def print_summary(report, path):
    print(f"{report['mode']} / {report['provider']}")
    for step in report["steps"]:
        result = step["result"]
        orders = ", ".join(result["order_ids"]) or "번호 대기"
        print(f"{step['step']} | {result['status']} | {orders}")
        print(f"  답변: {result['answer']}")
    print(f"전체 결과 파일: .local/{path.name}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--live", action="store_true")
    parser.add_argument("--provider", choices=("openai", "gemini"), default="openai")
    args = parser.parse_args()
    if args.live:
        if args.provider == "openai":
            from shipping.openai_boundary import live_model
        else:
            from shipping.model_boundary import live_model
        model = live_model()
    else:
        model = WorkflowModel()
    print("배송 문의 사례를 실행합니다. 완료 후 전체 결과를 파일로 저장합니다.", flush=True)
    report = {"mode": "LIVE" if args.live else "SCRIPTED_OFFLINE",
              "provider": args.provider if args.live else "scripted",
              "steps": run_demo(model)}
    output_path = save_report(report)
    print_summary(report, output_path)


if __name__ == "__main__":
    main()
