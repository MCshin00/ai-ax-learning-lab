"""항목별 검사 결과를 보존하고 메모 전체의 후속 행동을 정합니다."""
import json

from business import check_tasks, invalid


def review(memo, extraction_json):
    if not isinstance(memo, str) or not memo.strip():
        return invalid("회의 메모가 필요합니다.")
    if not isinstance(extraction_json, str):
        return invalid("추출 결과는 JSON 문자열이어야 합니다.")
    try:
        extracted = json.loads(extraction_json)
    except json.JSONDecodeError:
        return invalid("JSON을 읽을 수 없습니다.")
    if not isinstance(extracted, dict) or not isinstance(extracted.get("tasks"), list):
        return invalid("tasks 배열이 필요합니다.")

    try:
        checked = check_tasks(memo, extracted["tasks"])
    except ValueError as error:
        # 제공 검사 부품의 형식 오류 안내를 그대로 반환합니다.
        return {"status": "invalid", "tasks": [], "summary": str(error)}

    status = "ready" if checked and all(task["status"] == "ready" for task in checked) else "review"
    lines = [
        f"{task['title'] or '(내용 없음)'} | {', '.join(task['owners']) or '담당자 미정'} | "
        f"{task['due'] or '기한 미정'} | {'; '.join(task['reasons']) or '필드 검사 통과'}"
        for task in checked
    ]
    return {
        "status": status,
        "tasks": checked,
        "summary": "\n".join(lines) if lines else "추출된 작업이 없어 원문 검토가 필요합니다.",
    }
