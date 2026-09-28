"""같은 소수 입력의 결과를 나란히 읽습니다. 의미 정확성 점수를 자동으로 만들지 않습니다."""
import argparse
import json
import time
from pathlib import Path
from agent import CustomerAgent, POLICY
from business import OrderLookup, PolicySearch, load_orders, load_policies


def run_cases(make_agent, cases, clock=time.perf_counter):
    rows = []
    for variant in ("base", "grouped"):
        app = make_agent(variant)
        for case in cases:
            turns = []
            for query in case["turns"]:
                started = clock()
                result = app.reply(case["id"], query)
                turns.append({"query": query, "elapsed_seconds": round(clock() - started, 3), **result})
            actual = {call["name"] for turn in turns for call in turn["tool_calls"]}
            rows.append({"variant": variant, "case": case["id"], "turns": turns,
                "missing_tools": sorted(set(case["required_tools"]) - actual),
                "extra_tools_to_review": sorted(set(case["unnecessary_tools"]) & actual),
                "semantic_review": "NOT_VERIFIED", "review_criterion": case["review"]})
    return rows


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--live", action="store_true")
    parser.add_argument("--structure", action="store_true", help="발췌·보완과 처음부터 전체 문맥 전달 비교")
    args = parser.parse_args()
    if args.structure:
        run_structure_comparison(args.live)
        return
    cases = json.loads((Path(__file__).parent / "data/quality_cases.json").read_text(encoding="utf-8"))
    if not args.live:
        print("실제 비교 입력과 판단 기준입니다. --live에서만 모델을 실행합니다.")
        print(json.dumps(cases, ensure_ascii=False, indent=2))
        return
    from model_boundary import live_dependencies
    model, embeddings = live_dependencies()
    lookup, search = OrderLookup(load_orders()), PolicySearch(embeddings, load_policies())
    def make_agent(variant):
        policy = POLICY + ("\n여러 주문의 답변은 주문별로 확인 사실·정책 조건·남은 확인을 묶어 쓰세요." if variant == "grouped" else "")
        return CustomerAgent(model, lookup, search, policy=policy)
    print(json.dumps(run_cases(make_agent, cases), ensure_ascii=False, indent=2))


def run_structure_comparison(live=False):
    from consultation import Consultation, PolicyEvidence
    from consultation_demo import DEMO_REQUEST, demo_consultation
    rows = []
    for name, options in [("excerpt_only", {"repair": False}),
                          ("bounded_expansion", {}), ("full_context", {"initial_context": "full"})]:
        if live:
            from model_boundary import live_dependencies
            model, embeddings = live_dependencies()
            app = Consultation(model, OrderLookup(load_orders()),
                PolicyEvidence(PolicySearch(embeddings, load_policies()), load_policies()), **options)
        else:
            app = demo_consultation(**options)
        result = app.reply("comparison", DEMO_REQUEST)
        rows.append({"variant": name, "mode": "LIVE" if live else "SCRIPTED_DEMO", **result})
    print(json.dumps(rows, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
