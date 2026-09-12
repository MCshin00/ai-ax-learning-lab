"""같은 부분 반환을 값 전달과 상태 갱신으로 읽는 비교. 모든 실행은 오프라인입니다."""

if __package__ in (None, ""):
    import _bootstrap

import json

from comparisons.business import fixed_answer, lookup, make_draft
from comparisons.flows import BUILDERS


def partial_lookup(state):
    result = lookup(state)
    return {key: result[key] for key in ("order", "status", "path")}


def merge_lookup(state):
    # 값을 전달하는 방식에서는 남길 입력과 이번 갱신을 여기서 합칩니다.
    return {**state, **partial_lookup(state)}


def compare_updates():
    rows = []
    for contract, lookup_step in (("partial", partial_lookup), ("explicit_merge", merge_lookup)):
        for method, builder in BUILDERS.items():
            received = []

            def observe_draft(state):
                received.append(state.get("issue"))
                return fixed_answer(state)

            run = builder(make_draft(observe_draft), lookup_step=lookup_step)
            result = run({"order_id": "O-100", "issue": "배송 예정일 문의"})
            rows.append({"contract": contract, "method": method,
                         "issue_at_draft": received[0], "issue_in_result": result.get("issue"),
                         "status": result["status"]})
    return rows


if __name__ == "__main__":
    for row in compare_updates():
        print(json.dumps(row, ensure_ascii=False))
