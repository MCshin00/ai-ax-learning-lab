from __future__ import annotations

import argparse
import subprocess
from pathlib import Path


def build_command(
    *,
    model: str,
    reasoning: str,
    profile: str | None,
    sandbox: str,
    approval_policy: str,
    ignore_user_config: bool,
) -> list[str]:
    command = [
        "codex",
        "exec",
        "--ephemeral",
        "--json",
        "--sandbox",
        sandbox,
        "--model",
        model,
        "-c",
        f'model_reasoning_effort="{reasoning}"',
        "-c",
        f'approval_policy="{approval_policy}"',
    ]
    if profile:
        command.extend(["--profile", profile])
    if ignore_user_config:
        command.append("--ignore-user-config")
    command.append("-")
    return command


def run(
    *,
    prompt_file: Path,
    events_file: Path,
    stderr_file: Path,
    model: str,
    reasoning: str,
    profile: str | None,
    sandbox: str,
    approval_policy: str,
    timeout_seconds: float,
    ignore_user_config: bool,
) -> int:
    if not prompt_file.is_file():
        raise FileNotFoundError(f"Prompt file not found: {prompt_file}")
    if timeout_seconds <= 0:
        raise ValueError("timeout_seconds must be greater than zero")
    prompt = prompt_file.read_text(encoding="utf-8")
    if not prompt.strip():
        raise ValueError(f"Prompt file is empty: {prompt_file}")

    command = build_command(
        model=model,
        reasoning=reasoning,
        profile=profile,
        sandbox=sandbox,
        approval_policy=approval_policy,
        ignore_user_config=ignore_user_config,
    )
    events_file.parent.mkdir(parents=True, exist_ok=True)
    try:
        with events_file.open("w", encoding="utf-8") as stdout, stderr_file.open(
            "w", encoding="utf-8"
        ) as stderr:
            completed = subprocess.run(
                command,
                input=prompt,
                text=True,
                stdout=stdout,
                stderr=stderr,
                timeout=timeout_seconds,
                check=False,
            )
        return completed.returncode
    except subprocess.TimeoutExpired:
        with stderr_file.open("a", encoding="utf-8") as stderr:
            stderr.write(f"\nCodex execution timed out after {timeout_seconds:.3f} seconds.\n")
        return 124


def main() -> int:
    parser = argparse.ArgumentParser(description="Run one pinned `codex exec --json` experiment.")
    parser.add_argument("--prompt", type=Path, required=True)
    parser.add_argument("--events", type=Path, required=True)
    parser.add_argument("--stderr", type=Path, required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--reasoning", required=True)
    parser.add_argument("--profile")
    parser.add_argument(
        "--sandbox",
        choices=("read-only", "workspace-write", "danger-full-access"),
        default="workspace-write",
    )
    parser.add_argument(
        "--approval-policy",
        choices=("untrusted", "on-failure", "on-request", "never"),
        default="never",
    )
    parser.add_argument("--timeout-seconds", type=float, default=1800)
    parser.add_argument("--ignore-user-config", action="store_true")
    args = parser.parse_args()
    return run(
        prompt_file=args.prompt,
        events_file=args.events,
        stderr_file=args.stderr,
        model=args.model,
        reasoning=args.reasoning,
        profile=args.profile,
        sandbox=args.sandbox,
        approval_policy=args.approval_policy,
        timeout_seconds=args.timeout_seconds,
        ignore_user_config=args.ignore_user_config,
    )


if __name__ == "__main__":
    raise SystemExit(main())
