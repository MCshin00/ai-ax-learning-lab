from __future__ import annotations

import os
import re
from dataclasses import dataclass
from pathlib import Path


WEEK_DIRECTORY_PATTERN = re.compile(
    r"^week\d{2}-[a-z0-9]+(?:-[a-z0-9]+)*$"
)
RUN_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]*$")


@dataclass(frozen=True)
class RunLayout:
    repo_root: Path
    week_directory: Path
    week_name: str
    run_id: str
    raw_directory: Path
    public_directory: Path
    scratch_directory: Path


def is_within(path: Path, parent: Path) -> bool:
    try:
        path.resolve(strict=False).relative_to(parent.resolve(strict=False))
        return True
    except ValueError:
        return False


def _absolute(path: Path) -> Path:
    return Path(os.path.abspath(path))


def _is_link_or_junction(path: Path) -> bool:
    is_junction = getattr(path, "is_junction", None)
    return path.is_symlink() or bool(is_junction and is_junction())


def _reject_symlink_components(path: Path, *, repo_root: Path) -> None:
    candidate = _absolute(path)
    root = _absolute(repo_root).resolve(strict=False)
    if not is_within(candidate, root):
        raise ValueError("Run directories must be inside the repository")
    while candidate.resolve(strict=False) != root:
        if _is_link_or_junction(candidate):
            raise ValueError(
                f"Run directory path must not contain links or junctions: {candidate}"
            )
        parent = candidate.parent
        if parent == candidate:
            raise ValueError("Run directories must be inside the repository")
        candidate = parent


def _relative_parts(path: Path, *, repo_root: Path, label: str) -> tuple[str, ...]:
    absolute = _absolute(path).resolve(strict=False)
    root = _absolute(repo_root).resolve(strict=False)
    try:
        return absolute.relative_to(root).parts
    except ValueError as exc:
        raise ValueError(f"{label} must be inside the repository") from exc


def _validate_week_and_run(week_name: str, run_id: str) -> None:
    if not WEEK_DIRECTORY_PATTERN.fullmatch(week_name):
        raise ValueError(
            "Week directory must use the exact weekNN-slug form "
            "(for example, week07-langchain-langgraph)"
        )
    if not RUN_ID_PATTERN.fullmatch(run_id):
        raise ValueError(
            "Run id must start with an ASCII letter or number and contain only "
            "letters, numbers, dots, underscores, or hyphens"
        )


def validate_raw_run_directory(*, repo_root: Path, raw_directory: Path) -> RunLayout:
    root_absolute = _absolute(repo_root)
    root = root_absolute.resolve(strict=False)
    if not root.is_dir():
        raise NotADirectoryError(f"Repository root not found: {repo_root}")

    parts = _relative_parts(raw_directory, repo_root=root_absolute, label="Raw directory")
    if len(parts) != 4 or parts[1:3] != (".local", "raw"):
        raise ValueError(
            "Raw directory must use exactly "
            "repo/weekNN-slug/.local/raw/<run-id>"
        )
    week_name, _, _, run_id = parts
    _validate_week_and_run(week_name, run_id)
    _reject_symlink_components(raw_directory, repo_root=root_absolute)

    raw = _absolute(raw_directory).resolve(strict=False)
    if not is_within(raw, root):
        raise ValueError("Raw directory must resolve inside the repository")
    week = (root / week_name).resolve(strict=False)
    public = week / "runs" / run_id
    return RunLayout(
        repo_root=root,
        week_directory=week,
        week_name=week_name,
        run_id=run_id,
        raw_directory=raw,
        public_directory=public,
        scratch_directory=week / ".local" / "scratch",
    )


def validate_run_pair(
    *,
    repo_root: Path,
    raw_directory: Path,
    public_directory: Path,
) -> RunLayout:
    layout = validate_raw_run_directory(
        repo_root=repo_root,
        raw_directory=raw_directory,
    )
    parts = _relative_parts(
        public_directory,
        repo_root=_absolute(repo_root),
        label="Public directory",
    )
    if len(parts) != 3 or parts[1] != "runs":
        raise ValueError(
            "Public directory must use exactly repo/weekNN-slug/runs/<run-id>"
        )
    week_name, _, run_id = parts
    _validate_week_and_run(week_name, run_id)
    if week_name != layout.week_name or run_id != layout.run_id:
        raise ValueError(
            "Raw and public directories must use the same week and run id"
        )
    _reject_symlink_components(public_directory, repo_root=_absolute(repo_root))
    public = _absolute(public_directory).resolve(strict=False)
    if public != layout.public_directory.resolve(strict=False):
        raise ValueError("Public directory does not resolve to the expected week run path")
    return RunLayout(
        repo_root=layout.repo_root,
        week_directory=layout.week_directory,
        week_name=layout.week_name,
        run_id=layout.run_id,
        raw_directory=layout.raw_directory,
        public_directory=public,
        scratch_directory=layout.scratch_directory,
    )


def _required_file_is_symlink(path: Path) -> bool:
    return path.is_symlink()


def _resolve_required_file(path: Path) -> Path:
    return path.resolve(strict=True)


def require_raw_file(raw_directory: Path, filename: str) -> Path:
    raw = raw_directory.resolve(strict=False)
    candidate = raw / filename
    if _required_file_is_symlink(candidate):
        raise ValueError(f"Required raw run file must not be a symlink: {filename}")
    if not candidate.is_file():
        raise FileNotFoundError(f"Required raw run file not found: {filename}")
    resolved = _resolve_required_file(candidate)
    if resolved.parent != raw or not is_within(resolved, raw):
        raise ValueError(
            f"Required raw run file must resolve directly inside the raw directory: {filename}"
        )
    return resolved


def validate_scratch_directory(layout: RunLayout) -> Path:
    scratch = layout.scratch_directory
    _reject_symlink_components(scratch, repo_root=layout.repo_root)
    resolved = scratch.resolve(strict=False)
    if resolved != layout.week_directory / ".local" / "scratch":
        raise ValueError("Scratch directory must resolve inside the same week .local directory")
    return resolved
