"""초기 설계 초안용 예시입니다. 이 함수·State 계약은 필수가 아니며 주차 가이드에 따라 바꿀 수 있습니다."""
from business import make_draft


def build_flow(draft):
    raise NotImplementedError("Day 3에서 입력·상태·분기·실행 방식을 선택하세요. 이 파일을 바꾸거나 새 진입점을 만들 수 있습니다.")


if __name__ == "__main__":
    run = build_flow(make_draft())
    print(run({"order_id": "O-100", "issue": "배송 상태를 알려 주세요."}))
