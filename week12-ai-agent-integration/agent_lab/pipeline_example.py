"""순서가 고정된 짧은 비교 예제. 같은 요구로 두 앱을 완성하는 과제는 아닙니다."""
def cancellation_pipeline(order_id, lookup, search):
    order = lookup(order_id)
    if order["status"] != "found":
        return {"order": order, "policy": None}
    query = "출고된 주문 반품" if order["facts"]["shipped"] else "출고 전 취소"
    return {"order": order, "policy": search(query)}
