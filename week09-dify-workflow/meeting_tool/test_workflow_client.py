import json
import unittest
from meeting_review import review
from workflow_client import DraftStore, process


class DeliveryTest(unittest.TestCase):
    def setUp(self):
        self.store = DraftStore(":memory:")
        self.addCleanup(self.store.close)
        self.memo = "민수가 금요일에 초안을 공유한다."
        self.task = {"title": "초안 공유", "owners": ["민수"], "due": "금요일", "evidence": [self.memo], "conflicts": []}

    def workflow(self, memo):
        return {"data": {"status": "succeeded", "outputs": {"payload": review(memo, json.dumps({"tasks": [self.task]}, ensure_ascii=False))}}}

    def run_entries(self, entries, workflow=None, confirm=lambda *args: True):
        return process(entries, workflow or self.workflow, self.store, confirm)

    def test_duplicate_delivery_and_changed_content_do_not_overwrite(self):
        entry = [{"id": "M1", "memo": self.memo}]
        self.assertEqual("saved", self.run_entries(entry)[0]["status"])
        self.assertEqual("already_saved", self.run_entries(entry)[0]["status"])
        self.task["title"] = "초안 공유 내용 확인"
        self.assertEqual("revision_required", self.run_entries(entry)[0]["status"])
        self.assertEqual(1, self.store.db.execute("SELECT COUNT(*) FROM drafts").fetchone()[0])

    def test_model_cannot_approve_and_missing_evidence_is_not_saved(self):
        entry = [{"id": "M1", "memo": self.memo}]
        def forged(memo):
            output = self.workflow(memo)
            output["data"]["outputs"]["payload"]["approved"] = True
            return output
        self.assertEqual("awaiting_confirmation", self.run_entries(entry, forged, lambda *args: False)[0]["status"])
        self.task["due"] = ""
        self.assertEqual("review", self.run_entries(entry)[0]["status"])
        self.assertEqual(0, self.store.db.execute("SELECT COUNT(*) FROM drafts").fetchone()[0])

    def test_middle_failure_preserves_independent_success_and_invalid_output_is_distinct(self):
        def call(memo):
            if memo == "fail":
                raise TimeoutError("private details")
            if memo == "invalid":
                return {"data": {"status": "succeeded", "outputs": {"payload": []}}}
            return self.workflow(memo)
        entries = [{"id": str(i), "memo": memo} for i, memo in enumerate([self.memo, "fail", self.memo, "invalid"])]
        self.assertEqual(["saved", "workflow_unavailable", "saved", "invalid_workflow_output"],
                         [r["status"] for r in self.run_entries(entries, call)])

    def test_malformed_envelope_does_not_abort_following_entries(self):
        for malformed in [None, [], {"data": None}, {"data": []}, {"data": "failed"},
                          {"data": {"status": "succeeded", "outputs": None}}]:
            def call(memo):
                return malformed if memo == "bad" else self.workflow(memo)
            with self.subTest(envelope=malformed):
                result = self.run_entries([{"id": "bad", "memo": "bad"}, {"id": "ok", "memo": self.memo}], call)
                self.assertEqual("invalid_workflow_output", result[0]["status"])
                self.assertIn(result[1]["status"], ("saved", "already_saved"))


if __name__ == "__main__":
    unittest.main()
