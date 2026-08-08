from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

try:
    from .capture_environment import build_report, relative_display
    from .parse_codex_jsonl import summarize
    from .path_contract import is_within, validate_raw_run_directory
except ImportError:  # Direct script execution.
    from capture_environment import build_report, relative_display
    from parse_codex_jsonl import summarize
    from path_contract import is_within, validate_raw_run_directory


def find_git_root(working_directory: Path) -> Path | None:
    cwd = working_directory.resolve()
    for candidate in (cwd, *cwd.parents):
        if (candidate / ".git").exists():
            return candidate
    try:
        completed = subprocess.run(
            ["git", "-C", str(cwd), "rev-parse", "--show-toplevel"],
            capture_output=True,
            text=True,
            timeout=10,
            check=False,
            errors="replace",
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    if completed.returncode != 0 or not completed.stdout.strip():
        return None
    return Path(completed.stdout.strip()).resolve()


def build_command(
    *,
    working_directory: Path,
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
        "--cd",
        str(working_directory.resolve()),
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


def validate_locations(
    *,
    repo_root: Path,
    working_directory: Path,
    output_directory: Path,
) -> None:
    root = repo_root.resolve()
    cwd = working_directory.resolve()
    layout = validate_raw_run_directory(
        repo_root=root,
        raw_directory=output_directory,
    )
    if not cwd.is_dir():
        raise NotADirectoryError(f"Working directory not found: {working_directory}")
    if not is_within(cwd, root):
        raise ValueError("Working directory must be inside the detected Git repository")
    if cwd == root:
        raise ValueError(
            "Refusing to run from the repository root. Choose a specific descendant "
            "such as a week lab or shared benchmark directory."
        )
    if is_within(layout.raw_directory, cwd) or is_within(cwd, layout.raw_directory):
        raise ValueError(
            "Working directory and raw output directory must not overlap"
        )


def preview_payload(
    *,
    command: list[str],
    working_directory: Path,
    output_directory: Path,
    prompt_file: Path,
    request_sha256: str,
) -> dict[str, object]:
    return {
        "working_directory": str(working_directory.resolve()),
        "raw_output_directory": str(output_directory.resolve()),
        "prompt_file": str(prompt_file.resolve()),
        "request_sha256": request_sha256,
        "command": command,
    }


def run(
    *,
    prompt_file: Path,
    output_directory: Path,
    working_directory: Path,
    model: str,
    reasoning: str,
    profile: str | None,
    sandbox: str,
    approval_policy: str,
    timeout_seconds: float,
    ignore_user_config: bool,
    dry_run: bool = False,
) -> int:
    if not prompt_file.is_file():
        raise FileNotFoundError(f"Prompt file not found: {prompt_file}")
    if timeout_seconds <= 0:
        raise ValueError("timeout_seconds must be greater than zero")
    prompt = prompt_file.read_text(encoding="utf-8")
    if not prompt.strip():
        raise ValueError(f"Prompt file is empty: {prompt_file}")
    request_sha256 = hashlib.sha256(prompt.encode("utf-8")).hexdigest()

    cwd = working_directory.resolve()
    output = output_directory.resolve()
    repo_root = find_git_root(cwd)
    if repo_root is None:
        raise ValueError("Working directory must be inside a Git repository")
    validate_locations(
        repo_root=repo_root,
        working_directory=cwd,
        output_directory=output,
    )
    if output.exists():
        raise FileExistsError(f"Raw output directory already exists: {output_directory}")

    command = build_command(
        working_directory=cwd,
        model=model,
        reasoning=reasoning,
        profile=profile,
        sandbox=sandbox,
        approval_policy=approval_policy,
        ignore_user_config=ignore_user_config,
    )
    print(
        json.dumps(
            preview_payload(
                command=command,
                working_directory=cwd,
                output_directory=output,
                prompt_file=prompt_file,
                request_sha256=request_sha256,
            ),
            ensure_ascii=False,
            indent=2,
        ),
        file=sys.stderr,
    )
    if dry_run:
        return 0

    output.mkdir(parents=True)
    request_file = output / "request.md"
    events_file = output / "events.jsonl"
    stderr_file = output / "stderr.log"
    metadata_file = output / "run.json"
    environment_file = output / "environment.json"
    summary_file = output / "summary.json"
    shutil.copyfile(prompt_file, request_file)
    environment_file.write_text(
        json.dumps(
            build_report(
                root=repo_root,
                cwd=cwd,
                model=model,
                reasoning=reasoning,
                profile=profile,
                approval_policy=approval_policy,
                sandbox=sandbox,
                ignore_user_config=ignore_user_config,
            ),
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    started_at = datetime.now(timezone.utc)
    started_monotonic = time.monotonic()
    timed_out = False
    try:
        with events_file.open("wb") as stdout, stderr_file.open("wb") as stderr:
            completed = subprocess.run(
                command,
                input=prompt.encode("utf-8"),
                stdout=stdout,
                stderr=stderr,
                timeout=timeout_seconds,
                check=False,
                cwd=cwd,
            )
        return_code = completed.returncode
    except subprocess.TimeoutExpired:
        timed_out = True
        return_code = 124
        with stderr_file.open("ab") as stderr:
            stderr.write(
                f"\nCodex execution timed out after {timeout_seconds:.3f} seconds.\n".encode(
                    "utf-8"
                )
            )

    finished_at = datetime.now(timezone.utc)
    metadata_file.write_text(
        json.dumps(
            {
                "run_id": output.name,
                "started_at": started_at.isoformat(),
                "finished_at": finished_at.isoformat(),
                "wall_seconds": round(time.monotonic() - started_monotonic, 3),
                "codex_exit_code": return_code,
                "timed_out": timed_out,
                "working_directory": relative_display(cwd, root=repo_root),
                "model": model,
                "reasoning_effort": reasoning,
                "profile": profile,
                "sandbox": sandbox,
                "approval_policy": approval_policy,
                "ignore_user_config": ignore_user_config,
                "request_sha256": request_sha256,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    summary_file.write_text(
        json.dumps(
            summarize(events_file, metadata_file), ensure_ascii=False, indent=2
        ),
        encoding="utf-8",
    )
    return return_code


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Run one pinned Codex exec experiment. Use this only after a manual pilot; "
            "the default read-only sandbox does not permit code changes."
        )
    )
    parser.add_argument("--prompt", type=Path, required=True)
    parser.add_argument("--working-directory", type=Path, required=True)
    parser.add_argument("--output-directory", type=Path, required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--reasoning", required=True)
    parser.add_argument("--profile")
    parser.add_argument(
        "--sandbox",
        choices=("read-only", "workspace-write"),
        default="read-only",
    )
    parser.add_argument(
        "--approval-policy",
        choices=("untrusted", "on-request", "never"),
        default="never",
    )
    parser.add_argument("--timeout-seconds", type=float, default=1800)
    parser.add_argument("--ignore-user-config", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    try:
        return run(
            prompt_file=args.prompt,
            output_directory=args.output_directory,
            working_directory=args.working_directory,
            model=args.model,
            reasoning=args.reasoning,
            profile=args.profile,
            sandbox=args.sandbox,
            approval_policy=args.approval_policy,
            timeout_seconds=args.timeout_seconds,
            ignore_user_config=args.ignore_user_config,
            dry_run=args.dry_run,
        )
    except (FileNotFoundError, FileExistsError, NotADirectoryError, ValueError) as exc:
        parser.error(str(exc))


if __name__ == "__main__":
    raise SystemExit(main())
