from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable


def run_version(command: list[str]) -> str:
    try:
        proc = subprocess.run(command, capture_output=True, text=True, timeout=10, check=False, errors="replace")
        text = (proc.stdout + "\n" + proc.stderr).strip()
        return text.splitlines()[0] if text else f"EXIT_{proc.returncode}"
    except (OSError, subprocess.TimeoutExpired) as exc:
        return f"NOT_AVAILABLE: {exc}"


def git(command: list[str]) -> str | None:
    try:
        proc = subprocess.run(["git", *command], capture_output=True, text=True, timeout=10, check=False)
        return proc.stdout.strip() if proc.returncode == 0 else None
    except (OSError, subprocess.TimeoutExpired):
        return None


def hash_file(path: Path) -> str:
    digest = hashlib.sha256()
    digest.update(path.read_bytes())
    return digest.hexdigest()


def discovery_layers(root: Path, cwd: Path) -> list[Path]:
    """Return the repository layers Codex can discover from root through CWD."""
    root = root.resolve()
    cwd = cwd.resolve()
    try:
        relative = cwd.relative_to(root)
    except ValueError:
        return [root]

    layers = [root]
    current = root
    for part in relative.parts:
        current /= part
        layers.append(current)
    return layers


def tracked_config_files(root: Path, cwd: Path) -> Iterable[Path]:
    seen: set[Path] = set()
    for layer in discovery_layers(root, cwd):
        candidates = [
            layer / "AGENTS.md",
            layer / "AGENTS.override.md",
            layer / ".codex" / "hooks.json",
            layer / ".codex" / "config.toml",
        ]
        for tree_root in (
            layer / ".agents" / "skills",
            layer / ".codex" / "hooks",
            layer / ".codex" / "agents",
        ):
            if tree_root.is_dir():
                candidates.extend(
                    sorted(
                        path
                        for path in tree_root.rglob("*")
                        if path.is_file()
                        and not any(
                            part in {"__pycache__", ".pytest_cache", ".mypy_cache", ".ruff_cache", "build"}
                            for part in path.relative_to(tree_root).parts
                        )
                        and path.suffix.lower() not in {".pyc", ".pyo"}
                    )
                )
        for path in candidates:
            if path.is_file() and path not in seen:
                seen.add(path)
                yield path


def main() -> int:
    parser = argparse.ArgumentParser(description="Capture a reproducible experiment environment snapshot.")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--reasoning", required=True)
    parser.add_argument("--profile", default="")
    parser.add_argument("--approval-policy", required=True)
    parser.add_argument("--sandbox", required=True)
    parser.add_argument("--ignore-user-config", action="store_true")
    args = parser.parse_args()

    root_text = git(["rev-parse", "--show-toplevel"])
    root = Path(root_text).resolve() if root_text else Path.cwd().resolve()
    cwd = Path.cwd().resolve()
    hashes = {
        str(path.relative_to(root)): hash_file(path)
        for path in tracked_config_files(root, cwd)
    }
    git_status = git(["status", "--porcelain"])
    report = {
        "recorded_at": datetime.now(timezone.utc).isoformat(),
        "cwd": str(cwd),
        "repo_root": str(root),
        "os": platform.platform(),
        "python": sys.version.replace("\n", " "),
        "tools": {
            "codex": run_version(["codex", "--version"]),
            "git": run_version(["git", "--version"]),
            "uv": run_version(["uv", "--version"]),
            "node": run_version(["node", "--version"]),
            "java": run_version(["java", "-version"]),
        },
        "git": {
            "commit": git(["rev-parse", "HEAD"]),
            "branch": git(["branch", "--show-current"]),
            "dirty": None if git_status is None else bool(git_status),
        },
        "codex": {
            "model": args.model,
            "reasoning_effort": args.reasoning,
            "profile": args.profile or None,
            "approval_policy": args.approval_policy,
            "sandbox": args.sandbox,
            "ignore_user_config": args.ignore_user_config,
        },
        "harness_file_sha256": hashes,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
