"""제공 업무 규칙. 문장의 의미를 다시 추론하거나 부족한 사실을 채우지 않습니다."""
import json


def invalid(reason):
    return {"status": "invalid", "tasks": [], "summary": f"추출 형식을 확인하세요: {reason}"}


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
    checked = []
    for index, task in enumerate(extracted["tasks"], start=1):
        if not isinstance(task, dict):
            return invalid(f"작업 {index}는 객체여야 합니다.")
        if any(not isinstance(task.get(name), str) for name in ("title", "due")):
            return invalid(f"작업 {index}의 title·due는 문자열이어야 합니다.")
        for name in ("owners", "evidence", "conflicts"):
            if not isinstance(task.get(name), list) or any(not isinstance(value, str)
                                                         for value in task[name]):
                return invalid(f"작업 {index}의 {name}은 문자열 배열이어야 합니다.")
        owners = list(dict.fromkeys(value.strip() for value in task["owners"] if value.strip()))
        title, due = task["title"].strip(), task["due"].strip()
        evidence = [value for value in task["evidence"] if value.strip()]
        conflicts = [value for value in task["conflicts"] if value.strip()]
        reasons = []
        if not title:
            reasons.append("작업 내용 필요")
        if len(owners) != 1:
            reasons.append("담당자 미정" if not owners else "담당자 상충 확인")
        if not due:
            reasons.append("기한 미정")
        if conflicts:
            reasons.append("미해결 상충: " + "; ".join(conflicts))
        if not evidence or any(value not in memo for value in evidence):
            reasons.append("원문에 있는 근거 문장 필요")
        joined = "\n".join(evidence)
        if any(owner not in joined for owner in owners) or (due and due not in joined):
            reasons.append("담당자·기한의 근거 확인")
        checked.append({"title": title, "owners": owners, "due": due,
                        "evidence": evidence, "conflicts": conflicts,
                        "status": "review" if reasons else "ready", "reasons": reasons})
    status = "ready" if checked and all(task["status"] == "ready" for task in checked) else "review"
    lines = [f"{task['title'] or '(내용 없음)'} | {', '.join(task['owners']) or '담당자 미정'} | "
             f"{task['due'] or '기한 미정'} | {'; '.join(task['reasons']) or '필드 검사 통과'}"
             for task in checked]
    return {"status": status, "tasks": checked,
            "summary": "\n".join(lines) if lines else "추출된 작업이 없어 원문 검토가 필요합니다."}


def check_tasks(memo, tasks):
    """항목별 판단만 재사용하는 접점. 메모 전체의 경로·출력 형식은 호출자가 고릅니다."""
    result = review(memo, json.dumps({"tasks": tasks}, ensure_ascii=False))
    if result["status"] == "invalid":
        raise ValueError(result["summary"])
    return result["tasks"]
