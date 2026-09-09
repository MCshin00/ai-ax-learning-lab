from __future__ import annotations

import json
import sys
from datetime import datetime, timezone
from pathlib import Path


def command_exit_code(payload: dict[str, object]) -> int | None:
    response = payload.get("tool_response") or payload.get("toolResponse") or {}
    if not isinstance(response, dict):
        return None
    value = response.get("exit_code", response.get("exitCode"))
    return value if isinstance(value, int) and not isinstance(value, bool) else None


def safe_record(payload: dict[str, object]) -> dict[str, object]:
    return {
        "recorded_at": datetime.now(timezone.utc).isoformat(),
        "tool_name": payload.get("tool_name") or payload.get("toolName"),
        "exit_code": command_exit_code(payload),
        "turn_id": payload.get("turn_id") or payload.get("turnId"),
    }


def hook_output(payload: dict[str, object]) -> dict[str, object]:
    exit_code = command_exit_code(payload)
    if exit_code == 0:
        return {}
    if exit_code is None:
        message = "명령의 종료 코드를 확인하지 못했습니다."
        context = (
            "종료 코드가 없으므로 성공으로 보고하지 마세요. 실제 도구 결과에서 "
            "실행 완료 여부와 오류를 확인하세요. 아직 실행 중이라면 완료 결과를 "
            "확인하고, 결과 형식이 다르면 Hook의 종료 코드 읽기를 점검하세요."
        )
    else:
        message = f"명령이 종료 코드 {exit_code}로 실패했습니다."
        context = (
            "실제 출력을 읽고 실행 환경 문제인지 기능의 기대값 불일치인지 구분하세요. "
            "원인을 수정한 뒤 같은 검사를 다시 실행하세요. 검사를 삭제하거나 "
            "요구에 근거한 기대값을 약화하지 마세요. 해결할 수 없으면 같은 명령을 "
            "반복하지 말고 원인과 필요한 정보를 보고하세요."
        )
    return {
        "systemMessage": message,
        "hookSpecificOutput": {
            "hookEventName": "PostToolUse",
            "additionalContext": context,
        },
    }


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError as exc:
        print(f"Invalid hook payload: {exc}", file=sys.stderr)
        return 2
    if not isinstance(payload, dict):
        print("Hook payload must be a JSON object.", file=sys.stderr)
        return 2

    cwd = Path(str(payload.get("cwd") or ".")).resolve()
    log_path = cwd.parent / ".local" / "raw" / "hook-events.jsonl"
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(safe_record(payload), ensure_ascii=False) + "\n")
    print(json.dumps(hook_output(payload), ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
