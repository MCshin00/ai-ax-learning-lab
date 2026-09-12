"""정정으로 바뀌는 사실·모델 문맥과 보충 횟수 정책을 모의 모델로 확인합니다."""
import unittest
from unittest.mock import Mock

from shipping.components import lookup_order
from shipping.agent import ShippingAgent
from shipping.graph import ShippingGraph
from shipping.support import WorkflowModel


class CorrectionTest(unittest.TestCase):
    def test_correction_replaces_facts_and_agent_context(self):
        for builder in (ShippingAgent, ShippingGraph):
            with self.subTest(builder=builder.__name__):
                lookup = Mock(side_effect=lookup_order)
                app = builder(WorkflowModel(), lookup=lookup)
                issue = "배송 상태와 도착 날짜 문의"
                app.start("A", issue, ["O-100"])
                for order_id, status in (("O-200", "ANSWERED"), ("O-999", "NOT_FOUND")):
                    result = app.correct("A", [order_id])
                    self.assertEqual(issue, result["issue"])
                    self.assertEqual([order_id], result["order_ids"])
                    self.assertEqual({order_id: lookup_order(order_id)}, result["orders"])
                    self.assertEqual(status, result["status"])
                    self.assertEqual([], result["next"])
                    self.assertNotIn("O-100", result["answer"])
                    if order_id == "O-999":
                        self.assertNotIn("배송 중", result["answer"])
                    if builder is ShippingAgent:
                        messages = app.snapshot("A").values["messages"]
                        self.assertNotIn("O-100", str(messages))
                        if order_id == "O-999":
                            self.assertNotIn("O-200", str(messages))
                self.assertEqual(["O-100", "O-200", "O-999"],
                                 [call.args[0] for call in lookup.call_args_list])

    def test_correction_resets_supplement_budget_and_retains_waiting_policy(self):
        for builder in (ShippingAgent, ShippingGraph):
            with self.subTest(builder=builder.__name__):
                app = builder(WorkflowModel())
                app.start("A", "배송 문의", [])
                with self.assertRaises(ValueError):
                    app.correct("A", ["O-100"])
                result = app.supplement("A", ["O-100"])
                self.assertEqual(1, result["attempts"])
                result = app.correct("A", [])
                self.assertEqual(("WAITING", 0), (result["status"], result["attempts"]))
                for count, status in ((1, "WAITING"), (2, "UNRESOLVED")):
                    result = app.supplement("A", [])
                    self.assertEqual((status, count), (result["status"], result["attempts"]))
                self.assertEqual("배송 문의", result["issue"])


if __name__ == "__main__":
    unittest.main()
