"""설계 실습에서 재사용할 업무·생성 부품. 애플리케이션의 상태 형태는 정하지 않습니다."""
ORDERS = {"O-100": {"shipping": "배송 준비"}, "O-200": {"shipping": "배송 중"}}


def lookup_order(order_id: str) -> dict | None:
    if not isinstance(order_id, str) or not order_id.strip():
        raise ValueError("주문 조회에는 비어 있지 않은 문자열 ID가 필요합니다.")
    data = ORDERS.get(order_id)
    return None if data is None else {"order_id": order_id, **data}


def fixed_reply(issue: str, facts: dict) -> str:
    return f"{facts['order_id']}: {facts['shipping']}. 문의 내용은 추가 사실로 확정하지 않았습니다."
