from __future__ import annotations

import argparse
import hashlib
import json
import platform
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable


CACHE_DIRECTORY_NAMES = {
    ".git",
    ".gradle",
    ".idea",
    ".mypy_cache",
    ".pytest_cache",
    ".ruff_cache",
    ".venv",
    "__pycache__",
    "build",
    "dist",
    "node_modules",
}
CACHE_FILE_SUFFIXES = {".pyc", ".pyo"}


def run_version(command: list[str]) -> str:
    try:
        proc = subprocess.run(
            command,
            capture_output=True,
            text=True,
            timeout=10,
            check=False,
            errors="replace",
        )
        text = (proc.stdout + "\n" + proc.stderr).strip()
        return text.splitlines()[0] if text else f"EXIT_{proc.returncode}"
    except (OSError, subprocess.TimeoutExpired) as exc:
        return f"NOT_AVAILABLE: {type(exc).__name__}"


def git(command: list[str], *, cwd: Path) -> str | None:
    try:
        proc = subprocess.run(
            ["git", "-C", str(cwd), *command],
            capture_output=True,
            text=True,
            timeout=10,
            check=False,
            errors="replace",
        )
        return proc.stdout.strip() if proc.returncode == 0 else None
    except (OSError, subprocess.TimeoutExpired):
        return None


def hash_file(path: Path) -> str:
    digest = hashlib.sha256()
    digest.update(path.read_bytes())
    return digest.hexdigest()


def discovery_layers(root: Path, cwd: Path) -> list[Path]:
    """Return repository layers from root through CWD, inclusive."""
    root = root.resolve()
    cwd = cwd.resolve()
    try:
        relative = cwd.relative_to(root)
    except ValueError as exc:
        raise ValueError(f"Working directory must be inside repository root: {cwd}") from exc

    layers = [root]
    current = root
    for part in relative.parts:
        current /= part
        layers.append(current)
    return layers


def is_cache_file(path: Path, *, tree_root: Path) -> bool:
    relative = path.relative_to(tree_root)
    return (
        any(part in CACHE_DIRECTORY_NAMES for part in relative.parts)
        or path.suffix.lower() in CACHE_FILE_SUFFIXES
    )


def files_in_tree(tree_root: Path) -> list[Path]:
    if not tree_root.is_dir():
        return []
    return sorted(
        path
        for path in tree_root.rglob("*")
        if path.is_file() and not is_cache_file(path, tree_root=tree_root)
    )


def tracked_config_files(root: Path, cwd: Path) -> Iterable[Path]:
    """Yield active project harness files without scanning unrelated siblings."""
    seen: set[Path] = set()
    for layer in discovery_layers(root, cwd):
        candidates = [
            layer / "AGENTS.md",
            layer / "AGENTS.override.md",
            layer / ".codex" / "hooks.json",
            layer / ".codex" / "config.toml",
        ]
        for tree_root in (
            layer / ".codex" / "hooks",
            layer / ".codex" / "scripts",
            layer / ".codex" / "agents",
            layer / ".agents" / "skills",
        ):
            candidates.extend(files_in_tree(tree_root))
        for path in candidates:
            resolved = path.resolve()
            if path.is_file() and resolved not in seen:
                seen.add(resolved)
                yield resolved


def relative_display(path: Path, *, root: Path) -> str:
    relative = path.resolve().relative_to(root.resolve())
    return relative.as_posix() or "."


def build_report(
    *,
    root: Path,
    cwd: Path,
    model: str,
    reasoning: str,
    profile: str | None,
    approval_policy: str,
    sandbox: str,
    ignore_user_config: bool,
) -> dict[str, object]:
    root = root.resolve()
    cwd = cwd.resolve()
    discovery_layers(root, cwd)
    hashes = {
        relative_display(path, root=root): hash_file(path)
        for path in tracked_config_files(root, cwd)
    }
    git_status = git(["status", "--porcelain"], cwd=root)
    return {
        "recorded_at": datetime.now(timezone.utc).isoformat(),
        "working_directory": relative_display(cwd, root=root),
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
            "commit": git(["rev-parse", "HEAD"], cwd=root),
            "branch": git(["branch", "--show-current"], cwd=root),
            "dirty": None if git_status is None else bool(git_status),
        },
        "codex": {
            "model": model,
            "reasoning_effort": reasoning,
            "profile": profile or None,
            "approval_policy": approval_policy,
            "sandbox": sandbox,
            "ignore_user_config": ignore_user_config,
        },
        "harness_file_sha256": hashes,
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Capture an absolute-path-free experiment environment snapshot."
    )
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repo-root", type=Path, required=True)
    parser.add_argument("--working-directory", type=Path, required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--reasoning", required=True)
    parser.add_argument("--profile", default="")
    parser.add_argument("--approval-policy", required=True)
    parser.add_argument("--sandbox", required=True)
    parser.add_argument("--ignore-user-config", action="store_true")
    args = parser.parse_args()

    root = args.repo_root.resolve()
    cwd = args.working_directory.resolve()
    if not root.is_dir():
        parser.error(f"Repository root is not a directory: {args.repo_root}")
    if not cwd.is_dir():
        parser.error(f"Working directory is not a directory: {args.working_directory}")
    report = build_report(
        root=root,
        cwd=cwd,
        model=args.model,
        reasoning=args.reasoning,
        profile=args.profile or None,
        approval_policy=args.approval_policy,
        sandbox=args.sandbox,
        ignore_user_config=args.ignore_user_config,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
