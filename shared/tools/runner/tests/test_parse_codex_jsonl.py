from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from runner.parse_codex_jsonl import summarize


class ParseCodexJsonlTests(unittest.TestCase):
    def test_item_lifecycles_count_once_and_keep_latest_call_details(self) -> None:
        events = []
        for item in (
            {"id": "cmd-1", "type": "command_execution", "command": "python -B tests.py"},
            {"id": "mcp-1", "type": "mcp_tool_call", "tool": "lookup"},
            {"id": "patch-1", "type": "file_change"},
        ):
            for event_type in ("item.started", "item.updated", "item.completed"):
                events.append(json.dumps({"type": event_type, "item": item}))
        result = summarize(self.write(events))
        self.assertEqual(1, result["command_execution_count"])
        self.assertEqual(["python -B tests.py"], result["commands"])
        self.assertEqual(1, result["mcp_call_count"])
        self.assertEqual(["lookup"], result["mcp_calls"])
        self.assertEqual(1, result["file_change_count"])
        self.assertEqual(3, result["event_counts"]["item.completed"])

    def test_distinct_invocations_of_the_same_command_are_not_collapsed(self) -> None:
        result = summarize(self.write([
            json.dumps({"type": "item.completed", "item": {"id": item_id, "type": "command_execution", "command": "run-tests"}})
            for item_id in ("one", "two")
        ]))
        self.assertEqual(2, result["command_execution_count"])

    def test_item_ids_are_scoped_to_the_thread(self) -> None:
        events = []
        for thread_id in ("thread-one", "thread-two"):
            events.extend([
                json.dumps({"type": "thread.started", "thread_id": thread_id}),
                json.dumps({"type": "item.completed", "item": {"id": "item_0", "type": "command_execution", "command": "run-tests"}}),
            ])
        self.assertEqual(2, summarize(self.write(events))["command_execution_count"])

    def test_legacy_items_without_ids_count_completion_only(self) -> None:
        item = {"type": "command_execution", "command": "run-tests"}
        result = summarize(self.write([
            json.dumps({"type": event_type, "item": item})
            for event_type in ("item.started", "item.updated", "item.completed")
        ]))
        self.assertEqual(1, result["command_execution_count"])

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
