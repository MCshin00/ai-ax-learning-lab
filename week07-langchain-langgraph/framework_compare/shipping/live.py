"""같은 제공 사례를 기존 학습 구성과 실제 Gemini 모델로 실행합니다."""
import argparse
import json

from shipping.components import lookup_order
from shipping.agent import ShippingAgent
from shipping.graph import ShippingGraph
from shipping.model_boundary import MIN_REQUEST_INTERVAL, live_model
from shipping.scenarios import run_case


def run(method, *, day=4, default_case="partial"):
    parser = argparse.ArgumentParser(description="Gemini로 배송 조회·보충·정정 비교")
    parser.add_argument("--case", choices=("lookup", "partial", "waiting", "correction"), default=default_case)
    args = parser.parse_args()
    model = live_model()
    calls = []

    def lookup(order_id):
        calls.append(order_id)
        return lookup_order(order_id)

    builder = {"langchain": ShippingAgent, "langgraph": ShippingGraph}[method]
    app = builder(model, lookup=lookup)
    print(f"Day {day} / LIVE_MODEL / {method} / {model.model}")
    print(f"Gemini 요청 사이에 최소 {MIN_REQUEST_INTERVAL:g}초 간격을 둡니다. 첫 요청도 기다립니다.", flush=True)
    for event in run_case(app, args.case):
        result = {
            "method": method, "mode": "LIVE_MODEL", "model": model.model,
            "case": args.case, **event, "lookup_calls": list(calls)
        }
        if method == "langchain":
            result["tool_events"] = app.tool_events(event.get("request_id", "A"))
        print(json.dumps(result, ensure_ascii=False, indent=2))