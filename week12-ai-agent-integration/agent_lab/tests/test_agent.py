"""오프라인 실행 경계 검사. 실제 모델의 선택·답변 품질 평가는 아닙니다."""
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from langchain_core.messages import AIMessage, HumanMessage, ToolMessage

from agent import CustomerAgent
from business import OrderLookup, PolicySearch, load_orders, load_policies
from model_boundary import live_dependencies
from offline import ScriptedModel, TopicEmbeddings, call, demo_model
from pipeline_example import cancellation_pipeline


class IntegrationTests(unittest.TestCase):
    def setUp(self):
        # 프레임워크의 선택적 추적 설정도 실제 환경 대신 빈 설정을 사용합니다.
        self.environment = patch("os.environ", {})
        self.environment.start()
        self.addCleanup(self.environment.stop)
        self.lookup = OrderLookup(load_orders())
        self.search = PolicySearch(TopicEmbeddings(), load_policies())

    def agent(self, responses, **kwargs):
        model = ScriptedModel(responses=responses)
        return CustomerAgent(model, self.lookup, self.search, **kwargs), model

    def test_actual_agent_calls_both_tools_and_returns_cited_source(self):
        agent = CustomerAgent(demo_model(), self.lookup, self.search)
        result = agent.reply("case", "A-102 취소")
        self.assertEqual("responded", result["status"])
        self.assertEqual(["lookup_order", "search_policy"], [row["name"] for row in result["tool_calls"]])
        self.assertFalse(result["tool_calls"][0]["result"]["facts"]["shipped"])
        self.assertEqual(["POL-CANCEL"], [row["source_id"] for row in result["sources"]])

    def test_lookup_only_and_pipeline_have_different_execution_paths(self):
        agent, _ = self.agent([call("lookup_order", {"order_id": "A-102"}, "o"),
                              AIMessage(content="A-102는 배송 준비입니다.")])
        result = agent.reply("case", "A-102 상태")
        self.assertEqual(["lookup_order"], [row["name"] for row in result["tool_calls"]])
        pipeline = cancellation_pipeline("A-102", self.lookup, self.search)
        self.assertEqual("POL-CANCEL", pipeline["policy"]["matches"][0]["source_id"])

    def test_followup_keeps_original_question_and_isolates_conversations(self):
        agent, model = self.agent([AIMessage(content="주문 번호를 알려 주세요."),
                                  call("lookup_order", {"order_id": "A-102"}, "o"),
                                  AIMessage(content="A-102는 배송 준비입니다."),
                                  AIMessage(content="어떤 주문인가요?")])
        agent.reply("one", "취소할 수 있나요?")
        agent.reply("one", "A-102예요")
        self.assertIn("취소할 수 있나요?", [m.content for m in model.seen[1] if isinstance(m, HumanMessage)])
        self.assertTrue(any(isinstance(m, ToolMessage) for m in model.seen[2]))
        agent.reply("two", "그 주문은요?")
        self.assertEqual(["그 주문은요?"], [m.content for m in model.seen[-1] if isinstance(m, HumanMessage)])
        self.assertFalse(any(isinstance(m, ToolMessage) for m in model.seen[-1]))

    def test_current_trace_does_not_relabel_previous_order_as_current(self):
        agent, _ = self.agent([
            call("lookup_order", {"order_id": "A-102"}, "first"), AIMessage(content="배송 준비"),
            call("lookup_order", {"order_id": "A-103"}, "second"), AIMessage(content="배송 중"),
        ])
        agent.reply("one", "A-102 상태")
        second = agent.reply("one", "이번에는 A-103은요?")
        self.assertEqual(["A-103"], [row["result"]["order_id"] for row in second["tool_calls"]])

    def test_policy_retrieval_passes_source_and_handles_no_evidence(self):
        match = self.search("취소 출고")
        self.assertEqual("found", match["status"])
        self.assertEqual("POL-CANCEL", match["matches"][0]["source_id"])
        self.assertIn("신청", match["matches"][0]["text"])
        self.assertEqual("no_evidence", self.search("보증")["status"])
        self.assertEqual("no_evidence", PolicySearch(TopicEmbeddings(), [])("취소")["status"])

    def test_no_results_and_network_failure_are_distinct_tool_results(self):
        def broken(query):
            raise TimeoutError("이 오류 전문은 응답에 전달하지 않습니다.")
        for search, expected in [(lambda query: {"status": "no_evidence", "matches": []}, "no_evidence"),
                                 (broken, "unavailable")]:
            with self.subTest(expected=expected):
                agent = CustomerAgent(ScriptedModel(responses=[
                    call("search_policy", {"query": "보증"}, "p"), AIMessage(content="확인할 수 없습니다.")]), self.lookup, search)
                result = agent.reply("one", "보증 조건")
                self.assertEqual(expected, result["tool_calls"][0]["result"]["status"])
                self.assertNotIn("오류 전문", str(result))

    def test_order_not_found_and_lookup_timeout_are_distinct(self):
        self.assertEqual("not_found", self.lookup("A-999")["status"])
        self.assertEqual("invalid_input", self.lookup("missing")["status"])
        def broken(order_id):
            raise TimeoutError("internal")
        agent = CustomerAgent(ScriptedModel(responses=[call("lookup_order", {"order_id": "A-102"}, "o"),
                                                       AIMessage(content="조회 실패")]), broken)
        self.assertEqual("unavailable", agent.reply("one", "A-102")["tool_calls"][0]["result"]["status"])

    def test_six_model_calls_stop_and_budget_resets_next_turn(self):
        calls = [call("lookup_order", {"order_id": "A-102"}, f"o-{i}") for i in range(6)]
        agent, model = self.agent(calls + [AIMessage(content="다음 문의입니다.")])
        result = agent.reply("one", "A-102 조회")
        self.assertEqual("limit_reached", result["status"])
        self.assertEqual(6, model.calls)
        self.assertEqual(6, len(result["tool_calls"]))
        self.assertEqual(6, result["completed_model_calls"])
        self.assertIsNone(result["token_usage"])
        following = agent.reply("one", "다음 문의")
        self.assertEqual("responded", following["status"])
        self.assertEqual(1, following["completed_model_calls"])
        self.assertEqual(7, model.calls)

    def test_usage_counts_only_current_turn_and_does_not_fill_missing_usage(self):
        usage = {"input_tokens": 10, "output_tokens": 3, "total_tokens": 13}
        tool_call = call("lookup_order", {"order_id": "A-102"}, "first")
        tool_call.usage_metadata = usage
        agent, _ = self.agent([tool_call, AIMessage(content="배송 준비", usage_metadata=usage),
                               AIMessage(content="다음 안내", usage_metadata=usage),
                               AIMessage(content="사용량 미제공")])
        first = agent.reply("one", "A-102 상태")
        self.assertEqual(2, first["completed_model_calls"])
        self.assertEqual(26, first["token_usage"]["total_tokens"])
        self.assertEqual(13, agent.reply("one", "다음 문의")["token_usage"]["total_tokens"])
        self.assertIsNone(agent.reply("one", "다음 문의")["token_usage"])

    def test_partial_success_keeps_each_order_and_source(self):
        for ids in [("A-999", "A-102", "A-103"), ("A-103", "A-102", "A-999")]:
            with self.subTest(ids=ids):
                batch = AIMessage(content="", tool_calls=[
                    {"name": "lookup_order", "args": {"order_id": oid}, "id": oid, "type": "tool_call"} for oid in ids])
                agent, _ = self.agent([batch, call("search_policy", {"query": "출고 취소 반품"}, "p"),
                    AIMessage(content="A-102 취소 신청 가능 [POL-CANCEL], A-103 배송 중 반품 조건 확인 [POL-RETURN], A-999 조회 불가")])
                result = agent.reply("one", "각각 취소할 수 있나요? " + ", ".join(ids))
                self.assertEqual("responded", result["status"])
                rows = {row["result"]["order_id"]: row["result"] for row in result["tool_calls"] if row["name"] == "lookup_order"}
                self.assertEqual("not_found", rows["A-999"]["status"])
                self.assertFalse(rows["A-102"]["facts"]["shipped"])
                self.assertTrue(rows["A-103"]["facts"]["shipped"])
                self.assertEqual({"POL-CANCEL", "POL-RETURN"}, {row["source_id"] for row in result["sources"]})

    def test_unsupported_citation_is_not_published(self):
        agent, _ = self.agent([AIMessage(content="환불 확정 [POL-INVENTED]")])
        result = agent.reply("one", "환불")
        self.assertEqual("unsupported_source", result["status"])
        self.assertNotIn("환불 확정", result["answer"])

    def test_withheld_answer_is_replaced_in_followup_context(self):
        invented = "A-102는 환불 확정입니다 [POL-INVENTED]"
        agent, model = self.agent([AIMessage(content=invented), AIMessage(content="다시 확인해야 합니다.")])
        first = agent.reply("one", "환불")
        agent.reply("one", "그럼 언제 받나요?")
        context = [message.content for message in model.seen[-1] if isinstance(message, AIMessage)]
        self.assertIn(first["answer"], context)
        self.assertNotIn(invented, context)

    def test_model_failure_after_tool_can_retry_without_dangling_calls(self):
        agent, model = self.agent([call("lookup_order", {"order_id": "A-102"}, "o"), TimeoutError("internal"),
                                  AIMessage(content="다시 확인할 주문 번호를 알려 주세요.")])
        self.assertEqual("unavailable", agent.reply("one", "A-102")["status"])
        retry = agent.reply("one", "다시 문의")
        self.assertEqual("responded", retry["status"])
        self.assertFalse(any(isinstance(m, ToolMessage) for m in model.seen[-1]))

    def test_live_factory_uses_only_injected_settings_and_factories(self):
        settings = {"AI_AX_LIVE": "1", "OPENAI_API_KEY": "fake-test-value", "OPENAI_MODEL": "fake-chat",
                    "OPENAI_EMBEDDING_MODEL": "fake-embedding"}
        model, embedding = live_dependencies(get_setting=settings.get, chat_factory=lambda **kwargs: kwargs,
                                             embedding_factory=lambda **kwargs: kwargs)
        self.assertEqual("fake-chat", model["model"])
        self.assertEqual("fake-embedding", embedding["model"])
        self.assertEqual(0, model["max_retries"])
        with self.assertRaises(ValueError):
            live_dependencies(get_setting=lambda key: None)


if __name__ == "__main__":
    unittest.main()
