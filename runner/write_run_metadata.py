from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(description="Write run metadata without shell-specific JSON escaping.")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--started-at", required=True)
    parser.add_argument("--finished-at", required=True)
    parser.add_argument("--wall-seconds", type=float, required=True)
    parser.add_argument("--exit-code", type=int, required=True)
    parser.add_argument("--sandbox", required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--reasoning", required=True)
    parser.add_argument("--profile", default="")
    parser.add_argument("--approval-policy", required=True)
    parser.add_argument("--timeout-seconds", type=float, required=True)
    parser.add_argument("--ignore-user-config", action="store_true")
    args = parser.parse_args()
    payload = {
        "run_id": args.run_id,
        "started_at": args.started_at,
        "finished_at": args.finished_at,
        "wall_seconds": args.wall_seconds,
        "codex_exit_code": args.exit_code,
        "sandbox": args.sandbox,
        "model": args.model,
        "reasoning_effort": args.reasoning,
        "profile": args.profile or None,
        "approval_policy": args.approval_policy,
        "timeout_seconds": args.timeout_seconds,
        "ignore_user_config": args.ignore_user_config,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
