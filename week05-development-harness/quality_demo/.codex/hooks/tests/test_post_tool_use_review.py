from __future__ import annotations

import importlib.util
import io
import json
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest.mock import patch


def load(name: str):
    path = Path(__file__).resolve().parents[1] / name
    spec = importlib.util.spec_from_file_location(path.stem, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Cannot load {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


POST = load("post_tool_use_review.py")


class PostToolUseHookTests(unittest.TestCase):
    def test_failed_command_returns_feedback_without_raw_output(self) -> None:
        payload = {
            "tool_name": "Bash",
            "tool_response": {"exit_code": 1, "output": "SECRET"},
        }
        result = POST.hook_output(payload)
        self.assertIn("종료 코드 1", result["systemMessage"])
        feedback = result["hookSpecificOutput"]
        self.assertEqual("PostToolUse", feedback["hookEventName"])
        self.assertIn("같은 검사를 다시 실행", feedback["additionalContext"])
        self.assertNotIn("SECRET", json.dumps(result))
        self.assertNotIn("SECRET", json.dumps(POST.safe_record(payload)))

    def test_successful_command_needs_no_feedback(self) -> None:
        for response in ({"exit_code": 0}, {"exitCode": 0}):
            with self.subTest(response=response):
                self.assertEqual({}, POST.hook_output({"tool_response": response}))

    def test_missing_or_invalid_exit_code_is_not_success(self) -> None:
        for response in ({}, {"exit_code": None}, {"exit_code": False}, {"exit_code": "0"}, "pending"):
            with self.subTest(response=response):
                result = POST.hook_output({"tool_response": response})
                self.assertIn("확인하지 못했습니다", result["systemMessage"])
                self.assertIn("실행 완료 여부", result["hookSpecificOutput"]["additionalContext"])

    def test_record_contains_only_minimal_event_fields(self) -> None:
        payload = {
            "toolName": "Bash",
            "turnId": "turn-1",
            "tool_input": {"command": "PRIVATE_COMMAND"},
            "toolResponse": {"exitCode": 2, "output": "PRIVATE_OUTPUT"},
        }
        record = POST.safe_record(payload)
        self.assertEqual({"recorded_at", "tool_name", "exit_code", "turn_id"}, set(record))
        self.assertEqual(2, record["exit_code"])
        self.assertEqual("Bash", record["tool_name"])
        self.assertEqual("turn-1", record["turn_id"])
        self.assertNotIn("PRIVATE", json.dumps(record))

    def test_invalid_payload_reports_error_without_creating_a_log(self) -> None:
        for raw in ("not-json", "[]", "null"):
            with self.subTest(raw=raw):
                stdout, stderr = io.StringIO(), io.StringIO()
                with patch.object(POST.sys, "stdin", io.StringIO(raw)), patch.object(POST.Path, "mkdir") as mkdir:
                    with redirect_stdout(stdout), redirect_stderr(stderr):
                        self.assertEqual(2, POST.main())
                self.assertEqual("", stdout.getvalue())
                self.assertTrue(stderr.getvalue())
                mkdir.assert_not_called()


if __name__ == "__main__":
    unittest.main()
