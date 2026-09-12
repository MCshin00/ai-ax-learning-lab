"""새로 연결한 요청별 보충과 빈 보충 종료를 확인합니다."""
import unittest
from unittest.mock import Mock

from shipping.components import lookup_order
from shipping.agent import ShippingAgent
from shipping.support import WorkflowModel


class ObservedModel(WorkflowModel):
    calls: int = 0

    def _generate(self, messages, **kwargs):
        self.calls += 1
        return super()._generate(messages, **kwargs)


class ShippingAgentTest(unittest.TestCase):
    def test_reverse_supplements_preserve_original_issues_in_new_turns(self):
        model = ObservedModel()
        agent = ShippingAgent(model)
        for request_id, issue in (("A", "배송 예정일 문의"), ("B", "현재 배송 상태 문의")):
            result = agent.start(request_id, issue, [])
            self.assertEqual(("WAITING", [], False),
                             (result["status"], result["next"], result["interrupted"]))
        self.assertEqual(0, model.calls)
        b = agent.supplement("B", ["O-200"])
        self.assertEqual("WAITING", agent.view("A")["status"])
        a = agent.supplement("A", ["O-100"])
        self.assertEqual(("배송 예정일 문의", ["O-100"]), (a["issue"], a["order_ids"]))
        self.assertEqual(("현재 배송 상태 문의", ["O-200"]), (b["issue"], b["order_ids"]))
        self.assertEqual("ANSWERED", a["status"])
        self.assertEqual("ANSWERED", b["status"])

    def test_empty_supplements_stop_without_model_or_lookup(self):
        model = ObservedModel()
        lookup = Mock(side_effect=lookup_order)
        agent = ShippingAgent(model, lookup=lookup)
        agent.start("A", "배송 문의", [])
        first = agent.supplement("A", [" "])
        second = agent.supplement("A", [])
        self.assertEqual(("WAITING", 1), (first["status"], first["attempts"]))
        self.assertEqual(("UNRESOLVED", 2), (second["status"], second["attempts"]))
        self.assertEqual("주문 번호 확인이 필요합니다.", second["answer"])
        self.assertEqual(0, model.calls)
        lookup.assert_not_called()
        with self.assertRaises(ValueError):
            agent.supplement("A", ["O-100"])


if __name__ == "__main__":
    unittest.main()
