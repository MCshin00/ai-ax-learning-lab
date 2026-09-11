import argparse
import json

from business import make_draft
from comparison import BUILDERS
from model_boundary import live_generator


def main():
    parser = argparse.ArgumentParser(description="동일한 주문 흐름의 세 가지 연결 방식")
    parser.add_argument("--method", choices=[*BUILDERS, "all"], default="all")
    parser.add_argument("--order-id", default="O-100")
    parser.add_argument("--issue", default="배송 상태를 알려 주세요.")
    parser.add_argument("--live", action="store_true")
    args = parser.parse_args()
    draft = make_draft(live_generator(), "LIVE_MODEL") if args.live else make_draft()
    selected = BUILDERS if args.method == "all" else {args.method: BUILDERS[args.method]}
    for name, builder in selected.items():
        result = builder(draft)({"order_id": args.order_id, "issue": args.issue})
        print(json.dumps({"method": name, **result}, ensure_ascii=False))


if __name__ == "__main__":
    main()
