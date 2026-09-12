"""같은 제공 입력으로 두 참고 구성을 실행합니다. 학습자가 선택한 API를 강제하는 검사기는 아닙니다."""

if __package__ in (None, ""):
    import _bootstrap

import argparse
import json

from shipping.model_boundary import live_model
from shipping.agent import ShippingAgent
from shipping.graph import ShippingGraph
from shipping.support import WorkflowModel
from shipping.scenarios import CASES, run_case


BUILDERS = {"langchain": ShippingAgent, "langgraph": ShippingGraph}


def main():
    parser = argparse.ArgumentParser(description="LangChain·LangGraph의 설계·상태·정책 적용 예제")
    parser.add_argument("--method", choices=[*BUILDERS, "both"], default="both")
    parser.add_argument("--case", choices=CASES, default="lookup")
    parser.add_argument("--live", action="store_true")
    args = parser.parse_args()
    if args.live and args.method == "both":
        parser.error("실제 모델은 --method로 한 구성을 지정해 차례로 실행하세요.")
    if args.live and args.case == "generation-failure":
        parser.error("제공 실패 시나리오는 모의 모델로 실행합니다.")
    selected = BUILDERS if args.method == "both" else {args.method: BUILDERS[args.method]}
    for name, builder in selected.items():
        model = live_model() if args.live else WorkflowModel(fail_draft_once=args.case == "generation-failure")
        for event in run_case(builder(model), args.case):
            print(json.dumps({"method": name, "case": args.case,
                              "mode": "LIVE_MODEL" if args.live else "SCRIPTED_MODEL", **event},
                             ensure_ascii=False))


if __name__ == "__main__":
    main()
