from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from runner.parse_codex_jsonl import summarize


class ParseCodexJsonlTests(unittest.TestCase):
    def write(self, lines: list[str]) -> Path:
        directory = Path(tempfile.mkdtemp())
        path = directory / "events.jsonl"
        path.write_text("\n".join(lines), encoding="utf-8")
        return path

    def test_usage_and_message(self) -> None:
        path = self.write([
            json.dumps({"type": "thread.started", "thread_id": "t-1"}),
            json.dumps({"type": "item.completed", "item": {"type": "agent_message", "text": "done"}}),
            json.dumps({"type": "turn.completed", "usage": {"input_tokens": 10, "output_tokens": 2}}),
        ])
        result = summarize(path)
        self.assertEqual(result["usage"]["input_tokens"], 10)
        self.assertEqual(result["final_agent_message"], "done")
        self.assertEqual(result["execution_status"], "completed")

    def test_corrupt_line_is_reported(self) -> None:
        path = self.write([json.dumps({"type": "turn.started"}), "NOT_JSON"])
        result = summarize(path)
        self.assertEqual(result["invalid_json_lines"], 1)
        self.assertEqual(result["parse_errors"][0]["line"], 2)
        self.assertEqual(result["runtime_errors"], [])
        self.assertEqual(result["parse_status"], "has_invalid_lines")
        self.assertEqual(result["execution_status"], "incomplete")

    def test_missing_usage_is_not_invented(self) -> None:
        path = self.write([json.dumps({"type": "turn.failed"})])
        result = summarize(path)
        self.assertIsNone(result["usage"])
        self.assertEqual(result["turn_failed_count"], 1)
        self.assertEqual(result["execution_status"], "failed")

    def test_runtime_error_is_not_counted_as_invalid_json(self) -> None:
        path = self.write([
            json.dumps({"type": "error", "message": "tool failed"}),
            json.dumps({"type": "turn.failed"}),
        ])
        result = summarize(path)
        self.assertEqual(result["invalid_json_lines"], 0)
        self.assertEqual(result["parse_errors"], [])
        self.assertEqual(result["runtime_errors"][0]["message"], "tool failed")

    def test_nonzero_metadata_exit_code_marks_run_failed(self) -> None:
        path = self.write([json.dumps({"type": "turn.completed"})])
        metadata = path.with_name("run.json")
        metadata.write_text(json.dumps({"codex_exit_code": 7}), encoding="utf-8")
        result = summarize(path, metadata)
        self.assertEqual(result["codex_exit_code"], 7)
        self.assertEqual(result["execution_status"], "failed")

    def test_utf8_bom_metadata_is_supported(self) -> None:
        path = self.write([json.dumps({"type": "turn.completed"})])
        metadata = path.with_name("run.json")
        metadata.write_text(
            json.dumps({"codex_exit_code": 0}),
            encoding="utf-8-sig",
        )
        result = summarize(path, metadata)
        self.assertEqual(result["codex_exit_code"], 0)
        self.assertEqual(result["execution_status"], "completed")

    def test_completed_turn_usage_wins_over_intermediate_usage(self) -> None:
        path = self.write([
            json.dumps({"type": "item.completed", "usage": {"input_tokens": 8, "output_tokens": 1}}),
            json.dumps({"type": "turn.completed", "usage": {"input_tokens": 10, "output_tokens": 2}}),
        ])
        result = summarize(path)
        self.assertEqual(result["usage"]["input_tokens"], 10)
        self.assertEqual(result["usage"]["output_tokens"], 2)
        self.assertEqual(result["usage_source"], "turn.completed")


if __name__ == "__main__":
    unittest.main()
