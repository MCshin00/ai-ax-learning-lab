"""설계 실습에서 재사용할 업무·생성 부품. 애플리케이션의 상태 형태는 정하지 않습니다."""
from business import ORDERS


def lookup_order(order_id: str) -> dict | None:
    if not isinstance(order_id, str) or not order_id.strip():
        raise ValueError("주문 조회에는 비어 있지 않은 문자열 ID가 필요합니다.")
    data = ORDERS.get(order_id)
    return None if data is None else {"order_id": order_id, **data}


def fixed_reply(issue: str, facts: dict) -> str:
    return f"{facts['order_id']}: {facts['shipping']}. 문의 내용은 추가 사실로 확정하지 않았습니다."


def live_reply():
    # 기존 모델 경계의 요청 형식만 맞춥니다. 학습자의 상태에 이 필드명을 강제하지 않습니다.
    from model_boundary import live_generator
    generate = live_generator()
    return lambda issue, facts: generate({"issue": issue, "order": facts})
