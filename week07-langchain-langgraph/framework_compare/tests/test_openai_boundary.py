"""설정 로더·인증 입력·SDK 응답을 대역으로 바꿔 OpenAI 연결을 확인합니다."""
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, ChatResult

from examples import context_window
from shipping import openai_boundary as boundary
from shipping.support import WorkflowModel


class OpenAIBoundaryTest(unittest.TestCase):
    def test_file_config_selects_openai_without_implicit_previous_context(self):
        settings = {"AI_AX_LIVE": "1", "OPENAI_API_KEY": "fake-test-key", "OPENAI_MODEL": "test-model"}
        with patch.object(boundary, "dotenv_values", return_value=settings) as loader, \
                patch.object(boundary.os, "getenv", return_value=None), \
                patch.object(boundary, "ShippingOpenAI") as model:
            self.assertIs(model.return_value, boundary.live_model())
        loader.assert_called_once_with(boundary.CONFIG_PATH)
        args = model.call_args.kwargs
        self.assertEqual("test-model", args["model"])
        self.assertEqual("fake-test-key", args["api_key"])
        self.assertTrue(args["use_responses_api"])
        self.assertFalse(args["use_previous_response_id"])

    def test_environment_fallback_and_missing_key(self):
        values = {"AI_AX_LIVE": "1", "OPENAI_API_KEY": "fake-env-key"}
        with patch.object(boundary, "dotenv_values", return_value={}), \
                patch.object(boundary.os, "getenv", side_effect=values.get), \
                patch.object(boundary, "ShippingOpenAI") as model:
            boundary.live_model()
            self.assertEqual(boundary.DEFAULT_MODEL, model.call_args.kwargs["model"])
            values.pop("OPENAI_API_KEY")
            with self.assertRaisesRegex(ValueError, "OPENAI_API_KEY"):
                boundary.live_model()
            values["AI_AX_LIVE"] = "0"
            with self.assertRaisesRegex(ValueError, "AI_AX_LIVE"):
                boundary.live_model()
            self.assertEqual(1, model.call_count)

    def test_final_text_is_normalized_but_tool_request_is_preserved(self):
        model = boundary.ShippingOpenAI.model_construct()
        tool_message = AIMessage(content=[{"type": "text", "text": "조회합니다"}],
            tool_calls=[{"name": "lookup_shipping", "args": {"order_id": "O-200"}, "id": "call-test"}])
        final_message = AIMessage(content=[{"type": "text", "text": "O-200: 배송 중"}])
        for message in (tool_message, final_message):
            response = ChatResult(generations=[ChatGeneration(message=message)])
            with patch.object(boundary.ChatOpenAI, "_generate", return_value=response):
                result = model._generate([]).generations[0].message
            if message.tool_calls:
                self.assertIs(tool_message, result)
                self.assertEqual("call-test", result.tool_calls[0]["id"])
            else:
                self.assertEqual("O-200: 배송 중", result.content)

    def test_live_example_defaults_to_openai_and_runs_existing_context_case(self):
        with tempfile.TemporaryDirectory() as directory, \
                patch.object(context_window, "OUTPUT_DIR", Path(directory)), \
                patch.object(boundary, "live_model", return_value=WorkflowModel()) as factory, \
                patch("sys.argv", ["context_window.py", "--live"]), \
                contextlib.redirect_stdout(io.StringIO()) as output:
            context_window.main()
            paths = list(Path(directory).glob("*.json"))
            self.assertEqual(1, len(paths))
            report = json.loads(paths[0].read_text(encoding="utf-8"))
        factory.assert_called_once_with()
        self.assertEqual("openai", report["provider"])
        self.assertEqual(["O-200"], report["steps"][2]["result"]["order_ids"])
        self.assertEqual(5, len(report["steps"]))
        self.assertEqual("B_SUPPLEMENT_O100", report["steps"][-1]["step"])
        self.assertIn("LIVE / openai", output.getvalue())
        self.assertIn(paths[0].name, output.getvalue())
        self.assertNotIn('"stored_messages"', output.getvalue())

    def test_saved_report_preserves_all_messages_and_previous_runs(self):
        report = {"mode": "SCRIPTED_OFFLINE", "provider": "scripted",
                  "steps": context_window.run_demo(WorkflowModel())}
        with tempfile.TemporaryDirectory() as directory, \
                patch.object(context_window, "OUTPUT_DIR", Path(directory)):
            first = context_window.save_report(report)
            second = context_window.save_report(report)
            self.assertNotEqual(first, second)
            self.assertEqual(report, json.loads(first.read_text(encoding="utf-8")))
            self.assertEqual(report, json.loads(second.read_text(encoding="utf-8")))


if __name__ == "__main__":
    unittest.main()
