from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any, Iterable

USAGE_FIELDS = ("input_tokens", "cached_input_tokens", "output_tokens", "reasoning_output_tokens")


def iter_jsonl(path: Path) -> Iterable[tuple[int, dict[str, Any] | None, str | None]]:
    with path.open("r", encoding="utf-8", errors="replace") as handle:
        for line_number, raw in enumerate(handle, start=1):
            line = raw.strip()
            if not line:
                continue
            try:
                value = json.loads(line)
                if not isinstance(value, dict):
                    yield line_number, None, "JSON value is not an object"
                else:
                    yield line_number, value, None
            except json.JSONDecodeError as exc:
                yield line_number, None, f"{exc.msg} at column {exc.colno}"


def summarize(path: Path) -> dict[str, Any]:
    event_counts: Counter[str] = Counter()
    item_counts: Counter[str] = Counter()
    completed_turn_usage: list[dict[str, int]] = []
    fallback_usage: list[dict[str, int]] = []
    parse_errors: list[dict[str, Any]] = []
    runtime_errors: list[dict[str, Any]] = []
    thread_ids: set[str] = set()
    commands: list[str] = []
    mcp_calls: list[str] = []
    final_agent_message: str | None = None
    total_objects = 0

    if not path.exists():
        return {
            "source": str(path),
            "exists": False,
            "event_counts": {},
            "item_counts": {},
            "usage": None,
            "invalid_json_lines": 0,
            "parse_errors": [],
            "runtime_errors": [{"line": None, "message": "File not found"}],
            "errors": [{"line": None, "message": "File not found"}],
        }

    for line_number, event, error in iter_jsonl(path):
        if error:
            parse_errors.append({"line": line_number, "error": error})
            continue
        assert event is not None
        total_objects += 1
        event_type = str(event.get("type", "UNKNOWN"))
        event_counts[event_type] += 1

        thread_id = event.get("thread_id") or event.get("threadId")
        if isinstance(thread_id, str):
            thread_ids.add(thread_id)

        event_usage = event.get("usage")
        if isinstance(event_usage, dict):
            normalized = {
                field: int(event_usage.get(field, 0))
                for field in USAGE_FIELDS
                if isinstance(event_usage.get(field, 0), int)
            }
            if event_type == "turn.completed":
                completed_turn_usage.append(normalized)
            else:
                fallback_usage.append(normalized)

        item = event.get("item")
        if isinstance(item, dict):
            item_type = str(item.get("type", "UNKNOWN"))
            item_counts[item_type] += 1
            if item_type == "command_execution":
                command = item.get("command")
                if isinstance(command, str):
                    commands.append(command)
            if item_type in {"mcp_tool_call", "mcp_call", "tool_call"}:
                name = item.get("name") or item.get("tool_name") or item.get("toolName")
                mcp_calls.append(str(name or "UNKNOWN"))
            if item_type == "agent_message":
                text = item.get("text") or item.get("content")
                if isinstance(text, str):
                    final_agent_message = text

        if event_type == "error":
            runtime_errors.append(
                {
                    "line": line_number,
                    "message": event.get("message") or event.get("error") or event,
                }
            )

    usage_events = completed_turn_usage or fallback_usage[-1:]
    usage = {field: 0 for field in USAGE_FIELDS}
    for event_usage in usage_events:
        for field in USAGE_FIELDS:
            usage[field] += event_usage.get(field, 0)
    usage_seen = bool(usage_events)

    return {
        "source": str(path),
        "exists": True,
        "json_objects": total_objects,
        "invalid_json_lines": len(parse_errors),
        "parse_errors": parse_errors,
        "runtime_errors": runtime_errors,
        "errors": [*parse_errors, *runtime_errors],
        "event_counts": dict(sorted(event_counts.items())),
        "item_counts": dict(sorted(item_counts.items())),
        "thread_ids": sorted(thread_ids),
        "usage": usage if usage_seen else None,
        "usage_source": (
            "turn.completed"
            if completed_turn_usage
            else ("last_event_with_usage" if fallback_usage else None)
        ),
        "command_execution_count": item_counts.get("command_execution", 0),
        "file_change_count": sum(item_counts.get(name, 0) for name in ("file_change", "apply_patch")),
        "mcp_call_count": len(mcp_calls),
        "mcp_calls": mcp_calls,
        "commands": commands,
        "turn_completed_count": event_counts.get("turn.completed", 0),
        "turn_failed_count": event_counts.get("turn.failed", 0),
        "final_agent_message": final_agent_message,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Summarize JSONL emitted by `codex exec --json`.")
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = summarize(args.input)
    rendered = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered)
    return 0 if result.get("exists") else 2


if __name__ == "__main__":
    sys.exit(main())
