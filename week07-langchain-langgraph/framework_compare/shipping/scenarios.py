"""배송 조회·보충·정정·실패 복구의 제공 입력."""

CASES = ("lookup", "missing", "partial", "waiting", "empty", "correction", "generation-failure")


def run_case(app, name):
    if name in ("lookup", "missing", "partial"):
        ids = {"lookup": ["O-100", "O-200"], "missing": ["O-999"], "partial": ["O-100", "O-999"]}[name]
        yield {"action": "start", **app.start("A", "배송 상태와 도착 날짜가 궁금합니다.", ids)}
    elif name == "waiting":
        for request_id, issue in (("A", "배송 예정일 문의"), ("B", "현재 배송 상태 문의")):
            yield {"action": "start", "request_id": request_id, **app.start(request_id, issue, [])}
        for request_id, ids in (("B", ["O-200"]), ("A", ["O-100"])):
            yield {"action": "supplement", "request_id": request_id, **app.supplement(request_id, ids)}
    elif name == "empty":
        yield {"action": "start", **app.start("A", "배송 문의", [])}
        for _ in range(2):
            yield {"action": "supplement", **app.supplement("A", [])}
    elif name == "correction":
        yield {"action": "start", **app.start("A", "배송 문의", ["O-100"])}
        for ids in (["O-200"], ["O-999"]):
            yield {"action": "correct", **app.correct("A", ids)}
    elif name == "generation-failure":
        try:
            app.start("A", "배송 문의", ["O-100"])
        except RuntimeError as error:
            if str(error) != "MODEL_UNAVAILABLE":
                raise
            yield {"action": "generation_failed", **app.view("A")}
        else:
            raise AssertionError("제공 모의 모델의 생성 실패가 발생하지 않았습니다.")
        yield {"action": "retry", **app.retry("A")}
    else:
        raise ValueError("제공 사례를 선택하세요.")
