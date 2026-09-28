"""제공 사례의 실제 모델 경계에서 기록 보존·문맥 선별·요청 분리를 확인합니다."""
import json
import unittest

from examples.context_window import run_demo
from shipping.support import WorkflowModel


class ContextDemoTest(unittest.TestCase):
    def test_correction_retains_history_but_sends_only_current_facts(self):
        model = WorkflowModel()
        reports = run_demo(model)
        self.assertIsNone(model.callbacks)
        corrected = reports[2]
        stored = json.dumps(corrected["stored_messages"], ensure_ascii=False)
        self.assertIn("O-100", stored)
        self.assertIn("O-200", stored)
        self.assertEqual(["O-200"], list(corrected["result"]["orders"]))
        self.assertEqual("O-200: 배송 중", corrected["result"]["answer"])
        self.assertEqual(2, len(corrected["model_requests"]))
        for request in corrected["model_requests"]:
            sent = json.dumps(request, ensure_ascii=False)
            self.assertIn("배송 상태를 알고 싶습니다.", sent)
            self.assertIn("O-200", sent)
            self.assertNotIn("O-100", sent)
        final_input = corrected["model_messages"]
        calls = [call for message in final_input for call in message.get("tool_calls", [])]
        results = [message for message in final_input if message["type"] == "tool"]
        self.assertEqual(1, len(calls))
        self.assertEqual([call["id"] for call in calls],
                         [message["tool_call_id"] for message in results])
        self.assertEqual({"order_id": "O-200"}, calls[0]["args"])
        self.assertIn("O-200", results[0]["content"])

    def test_waiting_has_no_model_request_and_second_conversation_is_separate(self):
        reports = run_demo(WorkflowModel())
        for index in (0, 3):
            self.assertEqual("WAITING", reports[index]["result"]["status"])
            self.assertEqual([], reports[index]["model_requests"])
            self.assertEqual([], reports[index]["model_messages"])
        other = reports[4]
        self.assertEqual(["O-100"], list(other["result"]["orders"]))
        for messages in [other["stored_messages"], *other["model_requests"]]:
            text = json.dumps(messages, ensure_ascii=False)
            self.assertIn("별도 주문의 도착 날짜가 궁금합니다.", text)
            self.assertNotIn("배송 상태를 알고 싶습니다.", text)
            self.assertNotIn("O-200", text)


if __name__ == "__main__":
    unittest.main()
