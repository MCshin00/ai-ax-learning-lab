import unittest
from flow_patterns import route, collect_orders, revise_once
from compare_answers import run_cases


class PatternTest(unittest.TestCase):
    def test_status_routing_does_not_search_and_parallel_results_keep_order_ids(self):
        def lookup(order_id):
            if order_id == "missing":
                raise TimeoutError()
            return {"order_id": order_id, "status": "found"}
        self.assertEqual({"order": lookup("A")}, route("status", "A", lookup,
                         lambda query: self.fail("상태 조회에는 검색이 필요하지 않습니다.")))
        results = collect_orders(["A", "missing", "B"], lookup)
        self.assertEqual(["A", "missing", "B"], list(results))
        self.assertEqual("unavailable", results["missing"]["status"])
        self.assertEqual("B", results["B"]["order_id"])

    def test_unsolved_revision_stops_and_success_is_checked(self):
        inspect = lambda value: [] if value == "fixed" else ["근거 부족"]
        self.assertEqual("needs_review", revise_once("draft", inspect, lambda *args: "draft")["status"])
        self.assertEqual("accepted", revise_once("draft", inspect, lambda *args: "fixed")["status"])

    def test_comparison_keeps_followup_and_never_invents_quality_or_tokens(self):
        class App:
            def reply(self, conversation_id, query):
                return {"answer": query, "status": "responded", "tool_calls": [], "token_usage": None}
        rows = run_cases(lambda variant: App(), [{"id": "case", "turns": ["질문", "보충"],
            "required_tools": ["lookup_order"], "unnecessary_tools": [], "review": "근거 확인"}], clock=lambda: 1)
        self.assertEqual(2, len(rows))
        self.assertEqual(2, len(rows[0]["turns"]))
        self.assertEqual(["lookup_order"], rows[0]["missing_tools"])
        self.assertEqual("NOT_VERIFIED", rows[0]["semantic_review"])
        self.assertIsNone(rows[0]["turns"][0]["token_usage"])


if __name__ == "__main__":
    unittest.main()
