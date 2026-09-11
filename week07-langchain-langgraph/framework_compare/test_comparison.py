import unittest

from business import make_draft
from comparison import BUILDERS


class ComparisonTest(unittest.TestCase):
    def test_same_business_result_and_path(self):
        for order_id, status, path in [
            ("O-100", "ANSWERED", ["prepare", "lookup", "draft"]),
            ("", "NEEDS_INPUT", ["prepare", "ask"]),
            ("O-999", "NOT_FOUND", ["prepare", "lookup", "unavailable"]),
        ]:
            outcomes = [builder(make_draft())({"order_id": order_id}) for builder in BUILDERS.values()]
            self.assertEqual(outcomes[0], outcomes[1])
            self.assertEqual(outcomes[1], outcomes[2])
            self.assertEqual(status, outcomes[0]["status"])
            self.assertEqual(path, outcomes[0]["path"])

    def test_correction_does_not_reuse_old_order_or_answer(self):
        for builder in BUILDERS.values():
            run = builder(make_draft())
            old = run({"order_id": "O-100", "issue": "배송 문의"})
            new = run({**old, "order_id": "O-200"})
            self.assertEqual("배송 중", new["order"]["shipping"])
            self.assertEqual(old["issue"], new["issue"])
            failed = run({**new, "order_id": "O-999"})
            self.assertIsNone(failed["order"])
            self.assertNotIn("배송 중", failed["answer"])

    def test_model_is_not_called_without_order_facts(self):
        def broken_model(state):
            raise RuntimeError("MODEL_FAILED")
        for builder in BUILDERS.values():
            run = builder(make_draft(broken_model))
            self.assertEqual("NEEDS_INPUT", run({"order_id": ""})["status"])
            self.assertEqual("NOT_FOUND", run({"order_id": "O-999"})["status"])
            with self.assertRaisesRegex(RuntimeError, "MODEL_FAILED"):
                run({"order_id": "O-100"})


if __name__ == "__main__":
    unittest.main()
