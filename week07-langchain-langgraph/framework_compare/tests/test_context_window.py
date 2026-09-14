import unittest
from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from shipping.agent import ShippingAgent, current_turn_messages
from shipping.support import WorkflowModel


class ContextWindowTest(unittest.TestCase):
    def test_retention_does_not_send_stale_facts_and_preserves_original_issue(self):
        app = ShippingAgent(WorkflowModel(), retain_history=True)
        app.start("A", "원래 배송 문의", [])
        app.supplement("A", ["O-100"])
        result = app.correct("A", ["O-200"])
        stored = str(app.snapshot("A").values["messages"])
        sent = str(app.policy.last_model_messages)
        self.assertIn("O-100", stored)
        self.assertNotIn("O-100", sent)
        self.assertIn("원래 배송 문의", sent)
        self.assertIn("O-200", sent)
        self.assertEqual(["O-200"], list(result["orders"]))
        self.assertNotIn("O-100", str(app.tool_events("A")))
        self.assertIn("O-200", str(app.tool_events("A")))
        app.start("B", "다른 문의", ["O-100"])
        self.assertNotIn("원래 배송 문의", str(app.policy.last_model_messages))

    def test_selection_keeps_current_tool_call_and_result_together(self):
        current = [HumanMessage(content="현재 문의"),
            AIMessage(content="", tool_calls=[{"name": "lookup", "args": {}, "id": "c"}]),
            ToolMessage(content="조회 사실", tool_call_id="c")]
        self.assertEqual(current, current_turn_messages([HumanMessage(content="오래된 문의"),
                                                       AIMessage(content="과거 답변"), *current]))
        with self.assertRaises(ValueError):
            current_turn_messages([AIMessage(content="입력 없음")])


if __name__ == "__main__":
    unittest.main()
