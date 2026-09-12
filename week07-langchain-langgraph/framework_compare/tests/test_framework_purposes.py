import unittest

from langchain.agents.middleware.model_call_limit import ModelCallLimitExceededError
from langchain_core.messages import AIMessage, ToolMessage
from langchain_core.outputs import ChatGeneration, ChatResult

from examples.state_updates import compare_updates
from examples.tool_loop import AGENT_BUILDERS, ScriptedOrderModel
from examples.waiting_compare import WAITING_BUILDERS


class FrameworkPurposeTest(unittest.TestCase):
    def test_partial_update_and_explicit_merge_reach_answer_step(self):
        rows = {(row["contract"], row["method"]): row for row in compare_updates()}
        for method in ("direct", "langchain", "langgraph"):
            partial = rows["partial", method]
            expected = "배송 예정일 문의" if method == "langgraph" else None
            self.assertEqual(expected, partial["issue_at_draft"])
            self.assertEqual(expected, partial["issue_in_result"])
            merged = rows["explicit_merge", method]
            self.assertEqual("배송 예정일 문의", merged["issue_at_draft"])
            self.assertEqual("배송 예정일 문의", merged["issue_in_result"])

    def test_tools_execute_and_results_correlate_before_final_answer(self):
        for method, builder in AGENT_BUILDERS.items():
            for issue, ids, fragments in (
                ("O-100 배송", ["O-100"], ["배송 준비"]),
                ("O-100과 O-200 배송", ["O-100", "O-200"], ["배송 준비", "배송 중"]),
                ("배송 문의", [], ["주문 번호"]),
                ("O-999 배송", ["O-999"], ["찾지 못했습니다"]),
            ):
                with self.subTest(method=method, issue=issue):
                    messages = builder(ScriptedOrderModel())(issue)["messages"]
                    calls = [call for message in messages if isinstance(message, AIMessage)
                             for call in message.tool_calls]
                    results = [message for message in messages if isinstance(message, ToolMessage)]
                    self.assertEqual(ids, [call["args"]["order_id"] for call in calls])
                    self.assertEqual([call["id"] for call in calls], [m.tool_call_id for m in results])
                    self.assertTrue(all(m.status == "success" for m in results))
                    self.assertEqual(2 if ids else 1, sum(isinstance(m, AIMessage) for m in messages))
                    for fragment in fragments:
                        self.assertIn(fragment, messages[-1].content)

    def test_unknown_tool_returns_error_to_model_without_executing_business_action(self):
        class UnknownToolModel(ScriptedOrderModel):
            def _generate(self, messages, **kwargs):
                if isinstance(messages[-1], ToolMessage):
                    response = AIMessage(content="사용 가능한 도구를 확인해야 합니다.")
                else:
                    response = AIMessage(content="", tool_calls=[
                        {"name": "change_order", "args": {}, "id": "unknown", "type": "tool_call"}
                    ])
                return ChatResult(generations=[ChatGeneration(message=response)])

        for builder in AGENT_BUILDERS.values():
            messages = builder(UnknownToolModel())("배송 문의")["messages"]
            errors = [m for m in messages if isinstance(m, ToolMessage)]
            self.assertEqual(1, len(errors))
            self.assertEqual("error", errors[0].status)
            self.assertEqual("unknown", errors[0].tool_call_id)

    def test_waiting_requests_keep_their_issue_and_resume_position(self):
        for method, builder in WAITING_BUILDERS.items():
            received = []

            def answerer(issue, facts):
                received.append((issue, facts["order_id"]))
                return issue + ": " + facts["shipping"]

            flow = builder(answerer)
            with self.subTest(method=method):
                for request_id in ("A", "B"):
                    pending = flow.start(request_id, "문의 " + request_id)
                    self.assertEqual(["collect"], pending["next"])
                    self.assertEqual("문의 " + request_id, pending["question"]["issue"])
                for request_id, order_id in (("B", "O-200"), ("A", "O-100")):
                    final = flow.resume(request_id, order_id)
                    self.assertEqual("ANSWERED", final["status"])
                    self.assertEqual([], final["next"])
                    self.assertEqual("문의 " + request_id, final["issue"])
                self.assertEqual([("문의 B", "O-200"), ("문의 A", "O-100")], received)

    def test_repeated_tool_requests_stop_at_the_selected_model_call_limit(self):
        class RepeatingModel(ScriptedOrderModel):
            calls: int = 0

            def _generate(self, messages, **kwargs):
                self.calls += 1
                response = AIMessage(content="", tool_calls=[
                    {"name": "lookup_order", "args": {"order_id": "O-100"},
                     "id": f"repeat-{self.calls}", "type": "tool_call"}
                ])
                return ChatResult(generations=[ChatGeneration(message=response)])

        for method, builder in AGENT_BUILDERS.items():
            model = RepeatingModel()
            expected_error = RuntimeError if method == "direct" else ModelCallLimitExceededError
            with self.subTest(method=method), self.assertRaises(expected_error):
                builder(model, max_model_calls=2)("O-100 배송 문의")
            self.assertEqual(2, model.calls)

    def test_waiting_missing_order_and_empty_resumes_have_same_policy(self):
        outcomes = []
        for builder in WAITING_BUILDERS.values():
            def must_not_answer(issue, facts):
                self.fail("조회 사실이 없는데 생성 함수를 호출했습니다.")

            flow = builder(must_not_answer)
            flow.start("missing", "배송 문의")
            missing = flow.resume("missing", "O-999")
            self.assertEqual("NOT_FOUND", missing["status"])
            flow.start("empty", "원래 문의")
            first = flow.resume("empty", " ")
            second = flow.resume("empty", "")
            self.assertEqual(["collect"], first["next"])
            self.assertEqual("UNRESOLVED", second["status"])
            self.assertEqual(2, second["attempts"])
            self.assertEqual([], second["next"])
            with self.assertRaises(ValueError):
                flow.resume("empty", "O-100")
            with self.assertRaises(ValueError):
                flow.start("missing", "덮어쓸 문의")
            outcomes.append((missing, first, second))
        self.assertEqual(outcomes[0], outcomes[1])

    def test_generation_failure_does_not_ask_for_an_already_supplied_number(self):
        def broken_answerer(issue, facts):
            raise RuntimeError("MODEL_FAILED")

        snapshots = []
        for builder in WAITING_BUILDERS.values():
            flow = builder(broken_answerer)
            flow.start("request", "배송 문의")
            flow.resume("request", " ")
            with self.assertRaisesRegex(RuntimeError, "MODEL_FAILED"):
                flow.resume("request", "O-100")
            snapshot = flow.snapshot("request")
            self.assertEqual("O-100", snapshot["order_id"])
            self.assertEqual("READY", snapshot["status"])
            self.assertEqual(["answer"], snapshot["next"])
            self.assertEqual("", snapshot["answer"])
            self.assertIsNone(snapshot["question"])
            with self.assertRaises(ValueError):
                flow.resume("request", "O-100")
            snapshots.append(snapshot)
        self.assertEqual(snapshots[0], snapshots[1])


if __name__ == "__main__":
    unittest.main()
