import unittest

from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, ChatResult

from shipping.components import lookup_order
from shipping.agent import ShippingAgent
from shipping.graph import ShippingGraph
from shipping.support import WorkflowModel


class OrderWorkflowTest(unittest.TestCase):
    def test_waiting_uses_same_id_with_different_runtime_contracts(self):
        for builder in (ShippingAgent, ShippingGraph):
            app = builder(WorkflowModel())
            a = app.start("A", "문의 A", [])
            app.start("B", "문의 B", [])
            self.assertEqual("WAITING", a["status"])
            self.assertEqual(builder is ShippingGraph, a["interrupted"])
            self.assertEqual(["collect"] if builder is ShippingGraph else [], a["next"])
            b = app.supplement("B", ["O-200"])
            a = app.supplement("A", ["O-100"])
            self.assertEqual(("문의 A", ["O-100"]), (a["issue"], a["order_ids"]))
            self.assertEqual(("문의 B", ["O-200"]), (b["issue"], b["order_ids"]))

    def test_draft_retry_uses_saved_facts_without_repeating_lookup(self):
        for builder in (ShippingAgent, ShippingGraph):
            calls = []

            def lookup(order_id):
                calls.append(order_id)
                return lookup_order(order_id)

            app = builder(WorkflowModel(fail_draft_once=True), lookup=lookup)
            with self.assertRaisesRegex(RuntimeError, "MODEL_UNAVAILABLE"):
                app.start("A", "문의", ["O-100"])
            self.assertEqual("", app.view("A")["answer"])
            self.assertEqual("READY", app.view("A")["status"])
            self.assertEqual("ANSWERED", app.retry("A")["status"])
            self.assertEqual(["O-100"], calls)

    def test_invalid_draft_retry_calls_model_again_without_repeating_lookup(self):
        class EmptyDraftOnce(WorkflowModel):
            draft_calls: int = 0

            def _generate(self, messages, **kwargs):
                result = super()._generate(messages, **kwargs)
                if not result.generations[0].message.tool_calls:
                    self.draft_calls += 1
                    if self.draft_calls == 1:
                        return ChatResult(generations=[ChatGeneration(message=AIMessage(content=""))])
                return result

        for builder in (ShippingAgent, ShippingGraph):
            calls = []

            def lookup(order_id):
                calls.append(order_id)
                return lookup_order(order_id)

            model = EmptyDraftOnce()
            app = builder(model, lookup=lookup)
            with self.assertRaisesRegex(ValueError, "안내 문장"):
                app.start("A", "문의", ["O-100"])
            self.assertEqual("", app.view("A")["answer"])
            self.assertEqual("ANSWERED", app.retry("A")["status"])
            self.assertEqual(2, model.draft_calls)
            self.assertEqual(["O-100"], calls)

    def test_empty_policy_can_change_in_both_designs(self):
        for builder in (ShippingAgent, ShippingGraph):
            app = builder(WorkflowModel(), max_empty_replies=3)
            app.start("A", "문의", [])
            for _ in range(2):
                self.assertEqual("WAITING", app.supplement("A", [" "])["status"])
            self.assertEqual("UNRESOLVED", app.supplement("A", [])["status"])

    def test_agent_cannot_publish_without_lookup_or_query_an_unrequested_order(self):
        class PrematureModel(WorkflowModel):
            def _generate(self, messages, **kwargs):
                return ChatResult(generations=[ChatGeneration(message=AIMessage(content="내일 도착합니다."))])

        app = ShippingAgent(PrematureModel())
        result = app.start("A", "문의", ["O-100"])
        self.assertEqual("INCOMPLETE", result["status"])
        self.assertNotIn("내일", result["answer"])

        class WrongOrderModel(WorkflowModel):
            def _generate(self, messages, **kwargs):
                if messages[-1].type == "tool":
                    response = AIMessage(content="조회 실패")
                else:
                    response = AIMessage(content="", tool_calls=[
                        {"name": "lookup_shipping", "args": {"order_id": "O-200"},
                         "id": "wrong-order", "type": "tool_call"}])
                return ChatResult(generations=[ChatGeneration(message=response)])

        def must_not_lookup(order_id):
            self.fail("요청하지 않은 주문을 실제로 조회했습니다.")

        result = ShippingAgent(WrongOrderModel(), lookup=must_not_lookup).start("A", "문의", ["O-100"])
        self.assertEqual("INCOMPLETE", result["status"])
        self.assertEqual({}, result["orders"])


if __name__ == "__main__":
    unittest.main()
