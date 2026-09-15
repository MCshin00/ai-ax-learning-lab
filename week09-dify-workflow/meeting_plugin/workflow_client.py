"""Dify 출력 → 원문 대조 → 사람 확인 → 로컬 초안 저장. 기본 실행은 가짜 API입니다."""
import argparse
import hashlib
import json
import sqlite3
from pathlib import Path
from urllib import error, request
from business import review


class DraftStore:
    def __init__(self, path):
        self.db = sqlite3.connect(path)
        self.db.execute("CREATE TABLE IF NOT EXISTS drafts (id TEXT PRIMARY KEY, digest TEXT, payload TEXT)")

    def save(self, meeting_id, memo, checked):
        payload = json.dumps({"memo": memo, "tasks": checked["tasks"]}, ensure_ascii=False, sort_keys=True)
        digest = hashlib.sha256(payload.encode()).hexdigest()
        with self.db:
            cursor = self.db.execute("INSERT OR IGNORE INTO drafts VALUES (?, ?, ?)", (meeting_id, digest, payload))
            if cursor.rowcount:
                return "saved"
            previous = self.db.execute("SELECT digest FROM drafts WHERE id=?", (meeting_id,)).fetchone()[0]
            return "already_saved" if previous == digest else "revision_required"

    def close(self):
        self.db.close()


def process(entries, workflow, store, confirm):
    results = []
    for entry in entries:
        meeting_id, memo = entry["id"], entry["memo"]
        try:
            envelope = workflow(memo)
            if not isinstance(envelope, dict) or not isinstance(envelope.get("data"), dict):
                raise ValueError("Workflow 응답 객체가 필요합니다.")
            data = envelope["data"]
            if data.get("status") != "succeeded":
                raise ValueError("Workflow가 완료되지 않았습니다.")
            if not isinstance(data.get("outputs"), dict):
                raise ValueError("Workflow 출력 객체가 필요합니다.")
            payload = data["outputs"]["payload"]
            if isinstance(payload, str):
                payload = json.loads(payload)
            if not isinstance(payload, dict):
                raise ValueError("업무 결과 객체가 필요합니다.")
            checked = review(memo, json.dumps({"tasks": payload["tasks"]}, ensure_ascii=False))
            if payload.get("status") != checked["status"]:
                raise ValueError("Workflow 상태와 업무 결과가 일치하지 않습니다.")
        except (TimeoutError, ConnectionError, error.URLError):
            results.append({"id": meeting_id, "status": "workflow_unavailable"})
            continue
        except (ValueError, KeyError, TypeError):
            results.append({"id": meeting_id, "status": "invalid_workflow_output"})
            continue
        if checked["status"] != "ready":
            results.append({"id": meeting_id, **checked})
            continue
        # 확인 함수는 앱의 사람 입력입니다. 모델 출력에 approved=true를 추가해도 실행 권한이 되지 않습니다.
        if not confirm(meeting_id, memo, checked):
            results.append({"id": meeting_id, "status": "awaiting_confirmation"})
            continue
        try:
            status = store.save(meeting_id, memo, checked)
        except sqlite3.Error:
            status = "storage_error"
        results.append({"id": meeting_id, "status": status})
    return results


def live_workflow():
    # 실제 설정은 학습자가 --live를 선택했을 때만 읽습니다. 환경변수 파일을 로드하지 않습니다.
    import os
    endpoint, api_key = os.environ["DIFY_API_URL"].rstrip("/"), os.environ["DIFY_API_KEY"]
    def call(memo):
        body = json.dumps({"inputs": {"memo": memo}, "response_mode": "blocking", "user": "learning-demo"}).encode()
        req = request.Request(endpoint + "/workflows/run", data=body, method="POST",
            headers={"Authorization": "Bearer " + api_key, "Content-Type": "application/json",
                     "Accept": "application/json", "User-Agent": "MeetingReviewLab/1.0"})
        with request.urlopen(req, timeout=30) as response:
            return json.load(response)
    return call


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--live", action="store_true")
    args = parser.parse_args()
    cases = json.loads((Path(__file__).resolve().parent.parent / "workflow_materials/cases.json").read_text(encoding="utf-8"))
    entries = [case for case in cases if case["id"] in ("normal", "missing", "partial")]
    if args.live:
        workflow = live_workflow()
        folder = Path(".local")
        folder.mkdir(exist_ok=True)
        store = DraftStore(folder / "meeting-drafts.sqlite")
        def confirm(meeting_id, memo, checked):
            print(json.dumps({"id": meeting_id, "memo": memo, "checked": checked}, ensure_ascii=False, indent=2))
            return input("원문과 작업 내용을 확인하고 이 초안을 저장하려면 save 입력: ").strip() == "save"
    else:
        print("가짜 Dify 응답·메모리 DB: 실제 추출과 사람의 확인은 검증하지 않습니다.")
        fixtures = {case["memo"]: case["extraction"] for case in cases}
        def workflow(memo):
            return {"data": {"status": "succeeded", "outputs": {"payload": review(memo, json.dumps(fixtures[memo], ensure_ascii=False))}}}
        store, confirm = DraftStore(":memory:"), lambda *args: True
    try:
        for _ in range(2):
            print(json.dumps(process(entries, workflow, store, confirm), ensure_ascii=False, indent=2))
    finally:
        store.close()


if __name__ == "__main__":
    main()
