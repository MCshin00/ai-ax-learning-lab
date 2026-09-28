"""가짜 모델·임베딩으로 실제 create_agent와 앱의 제어·전달 경계를 확인합니다."""
import json
import sys
import threading
import unittest
from pathlib import Path
from urllib.request import Request, urlopen
from langchain_core.messages import AIMessage

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from business import OrderLookup, PolicySearch, load_orders, load_policies
from consultation import Consultation, PolicyEvidence
from consultation_demo import demo_consultation, DEMO_REQUEST, ReplayApp
from offline import ScriptedModel, TopicEmbeddings, call
from service import dispatch, make_server


def intake(ids=("A-102",), intent="cancel", **facts):
    return call("Intake", {"intent": intent, "order_ids": list(ids), **facts}, "intake")


def draft(ids=("A-102",), sources=("POL-CANCEL",), answer="조회한 정책의 조건과 신청 경로"):
    return call("ConsultationDraft", {"items": [dict(order_id=oid, answer=answer,
                                                   source_ids=list(sources)) for oid in ids]}, "draft")


def search(query="출고 전 취소"):
    return call("search_policy", {"query": query}, "search")


class ConsultationTests(unittest.TestCase):
    def make(self, responses, lookup=None, evidence=None, **options):
        model = ScriptedModel(responses=responses)
        evidence = evidence or PolicyEvidence(PolicySearch(TopicEmbeddings(), load_policies()), load_policies())
        return Consultation(model, lookup or OrderLookup(load_orders()), evidence, **options), model

    def test_real_agent_expands_evidence_and_rechecks_under_one_budget(self):
        app = demo_consultation()
        result = app.reply("one", DEMO_REQUEST)
        self.assertEqual("ready", result["status"])
        self.assertEqual(1, result["revisions"])
        self.assertEqual(4, result["model_calls"])
        self.assertEqual("full", result["items"][0]["sources"][0]["scope"])
        self.assertIn("신청 경로", result["items"][0]["sources"][0]["text"])
        self.assertEqual(["display"], [a["action"] for a in result["actions"]])
        self.assertEqual(["lookup", "search", "check", "expand", "check"], [e["stage"] for e in result["trace"]])

    def test_context_policy_changes_need_for_revision(self):
        base = demo_consultation(repair=False).reply("one", DEMO_REQUEST)
        full = demo_consultation(initial_context="full").reply("one", DEMO_REQUEST)
        self.assertEqual("needs_review", base["status"])
        self.assertEqual("ready", full["status"])
        self.assertEqual(0, full["revisions"])
        self.assertEqual(3, full["model_calls"])

    def test_empty_search_does_not_retry_without_new_information(self):
        evidence = PolicyEvidence(lambda q: {"status": "no_evidence", "matches": []}, [])
        app, model = self.make([intake(), search(), draft(sources=())], evidence=evidence)
        result = app.reply("one", DEMO_REQUEST)
        self.assertEqual("needs_review", result["status"])
        self.assertEqual(0, result["revisions"])
        self.assertEqual(3, model.calls)

    def test_unsolved_revision_stops_and_does_not_publish_fabricated_source(self):
        app, model = self.make([intake(), search(), draft(sources=("POL-INVENTED",)),
                               draft(sources=("POL-INVENTED",))])
        result = app.reply("one", DEMO_REQUEST)
        self.assertEqual("needs_review", result["status"])
        self.assertEqual(1, result["revisions"])
        self.assertEqual("", result["items"][0]["answer"])
        self.assertEqual(4, model.calls)

    def test_followup_correction_and_other_conversation(self):
        app, model = self.make([intake(ids=()), intake(intent="status"), draft(sources=()),
            intake(ids=("A-103",), intent="status"), draft(ids=("A-103",), sources=()), intake(ids=())])
        self.assertEqual("needs_input", app.reply("one", "상태가 궁금해요")["status"])
        self.assertEqual("A-102", app.reply("one", "A-102")["items"][0]["order_id"])
        corrected = app.reply("one", "A-103으로 정정할게요")
        self.assertEqual("배송 중", corrected["items"][0]["facts"]["status"])
        self.assertEqual("needs_input", app.reply("two", "취소할 수 있나요?")["status"])
        self.assertIsNone(json.loads(model.seen[-1][-1].content)["previous"])

    def test_missing_customer_facts_resume_without_copying_to_other_order(self):
        app, model = self.make([intake(ids=("A-104",), intent="return"), search("반품 수령"),
            draft(ids=("A-104",), sources=("POL-RETURN",)), draft(ids=("A-104",), sources=("POL-RETURN",)),
            intake(ids=("A-104",), intent="return", received_days={"A-104": 2}, used={"A-104": False}),
            search("반품 수령"), draft(ids=("A-104",), sources=("POL-RETURN",)),
            draft(ids=("A-104",), sources=("POL-RETURN",)), intake(intent="status"), draft(sources=())])
        self.assertEqual("ask", app.reply("one", "A-104 반품 문의")["actions"][0]["action"])
        self.assertEqual("ready", app.reply("one", "수령한 지 2일이고 사용하지 않았어요")["status"])
        corrected = app.reply("one", "A-102 상태를 알려 주세요")
        self.assertEqual({}, corrected["intake"]["received_days"])
        previous_input = json.loads(model.seen[-2][-1].content)["previous"]
        self.assertEqual({}, previous_input["received_days"])

    def test_partial_success_and_lookup_failure_preserve_facts(self):
        def lookup(oid):
            if oid == "A-103":
                raise TimeoutError()
            return OrderLookup(load_orders())(oid)
        ids = ("A-999", "A-102", "A-103")
        app, _ = self.make([intake(ids=ids, intent="status"), draft(ids=ids, sources=())], lookup=lookup)
        result = app.reply("one", "A-999 A-102 A-103 상태")
        self.assertEqual("partial", result["status"])
        self.assertEqual(["not_found", "ready", "unavailable"], [r["status"] for r in result["items"]])

    def test_budget_includes_intake_tool_loop_and_revision_and_resets(self):
        app = demo_consultation(max_model_calls=3)
        result = app.reply("one", DEMO_REQUEST)
        self.assertEqual("limit_reached", result["status"])
        self.assertEqual(3, result["model_calls"])
        self.assertEqual("배송 준비", result["items"][0]["facts"]["status"])
        app.model.responses = [intake(intent="status"), draft(sources=())]
        self.assertEqual(2, app.reply("one", "A-102 상태")["model_calls"])

    def test_provider_failure_keeps_facts_and_next_turn_can_retry(self):
        app, model = self.make([intake(intent="status"), TimeoutError(), intake(intent="status"), draft(sources=())])
        result = app.reply("one", "A-102 상태")
        self.assertEqual("unavailable", result["status"])
        self.assertTrue(result["items"][0]["facts"])
        self.assertEqual("ready", app.reply("one", "다시 확인해 주세요")["status"])

    def test_duplicate_structured_results_become_invalid_output(self):
        for stage in ("intake", "draft"):
            with self.subTest(stage=stage):
                message = intake(intent="status") if stage == "intake" else draft(sources=())
                duplicate = AIMessage(content="", tool_calls=[message.tool_calls[0],
                    {**message.tool_calls[0], "id": "duplicate"}])
                responses = [duplicate] if stage == "intake" else [intake(intent="status"), duplicate]
                app, _ = self.make(responses)
                result = app.reply("one", "A-102 상태")
                self.assertEqual("invalid_output", result["status"])
                if stage == "draft":
                    self.assertEqual("배송 준비", result["items"][0]["facts"]["status"])

    def test_order_number_boundaries_do_not_change_lookup_target(self):
        for text in ("A-1024", "XA-102", "A-102-X"):
            with self.subTest(text=text):
                app, _ = self.make([intake(ids=(text,), intent="status")])
                result = app.reply("one", text + " 상태")
                self.assertEqual("invalid_output", result["status"])
                self.assertEqual([], result["items"])
        app, _ = self.make([intake(intent="status"), draft(sources=())])
        self.assertEqual("ready", app.reply("one", "A-102예요")["status"])

    def test_api_contract_and_http_with_explicit_ephemeral_port(self):
        self.assertEqual(400, dispatch(ReplayApp(), {"request": DEMO_REQUEST})[0])
        with make_server(ReplayApp(), port=0) as server:
            worker = threading.Thread(target=server.handle_request)
            worker.start()
            try:
                request = Request(f"http://127.0.0.1:{server.server_port}/consultations",
                    data=json.dumps({"conversation_id": "api", "request": DEMO_REQUEST}).encode(),
                    headers={"Content-Type": "application/json"})
                with urlopen(request, timeout=10) as response:
                    value = json.load(response)
                    self.assertEqual(200, response.status)
                    self.assertEqual("api", value["conversation_id"])
                    self.assertEqual("ready", value["status"])
                    self.assertEqual("SCRIPTED_DEMO", value["mode"])
            finally:
                worker.join(timeout=10)
            self.assertFalse(worker.is_alive())


if __name__ == "__main__":
    unittest.main()
