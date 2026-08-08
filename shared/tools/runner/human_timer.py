from __future__ import annotations

import argparse
import json
import time
from datetime import datetime, timezone
from pathlib import Path

STATE = Path(".lab-state/active_timers.json")


def load_state() -> dict[str, dict[str, object]]:
    if not STATE.exists():
        return {}
    return json.loads(STATE.read_text(encoding="utf-8"))


def save_state(state: dict[str, dict[str, object]]) -> None:
    STATE.parent.mkdir(parents=True, exist_ok=True)
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")


def append_event(output_root: Path, run_id: str, event: dict[str, object]) -> Path:
    path = output_root / f"{run_id}.jsonl"
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(event, ensure_ascii=False) + "\n")
    return path


def main() -> int:
    parser = argparse.ArgumentParser(description="Measure active human time separately from wall time.")
    parser.add_argument(
        "--output-root",
        type=Path,
        required=True,
        help=(
            "Week-local timer directory, for example "
            "week01-codex-prompt-comparison/runs/timers."
        ),
    )
    sub = parser.add_subparsers(dest="action", required=True)
    start = sub.add_parser("start")
    start.add_argument("--run-id", required=True)
    start.add_argument("--activity", default="general")
    stop = sub.add_parser("stop")
    stop.add_argument("--run-id", required=True)
    stop.add_argument("--activity")
    status = sub.add_parser("status")
    status.add_argument("--run-id")
    args = parser.parse_args()

    state = load_state()
    now_iso = datetime.now(timezone.utc).isoformat()
    if args.action == "start":
        if args.run_id in state:
            raise SystemExit(f"Timer already active: {args.run_id}")
        state[args.run_id] = {"started_monotonic": time.monotonic(), "started_at": now_iso, "activity": args.activity}
        save_state(state)
        append_event(
            args.output_root,
            args.run_id,
            {"type": "start", "at": now_iso, "activity": args.activity},
        )
        print(f"STARTED {args.run_id} {args.activity}")
        return 0

    if args.action == "stop":
        active = state.pop(args.run_id, None)
        if not active:
            raise SystemExit(f"No active timer: {args.run_id}")
        duration = max(0.0, time.monotonic() - float(active["started_monotonic"]))
        activity = args.activity or str(active.get("activity", "general"))
        save_state(state)
        path = append_event(
            args.output_root,
            args.run_id,
            {
                "type": "stop",
                "at": now_iso,
                "activity": activity,
                "duration_seconds": round(duration, 3),
            },
        )
        print(json.dumps({"run_id": args.run_id, "activity": activity, "duration_seconds": round(duration, 3), "path": str(path)}, ensure_ascii=False))
        return 0

    selected = {args.run_id: state.get(args.run_id)} if args.run_id else state
    print(json.dumps(selected, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
