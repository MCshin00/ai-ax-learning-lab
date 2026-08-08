from __future__ import annotations

import argparse
import json
import re
import shutil
import tempfile
from pathlib import Path
from typing import Any, Iterable

try:
    from .parse_codex_jsonl import summarize
    from .path_contract import (
        is_within,
        require_raw_file,
        validate_run_pair,
        validate_scratch_directory,
    )
except ImportError:  # Direct script execution.
    from parse_codex_jsonl import summarize
    from path_contract import (
        is_within,
        require_raw_file,
        validate_run_pair,
        validate_scratch_directory,
    )


CAMEL_CASE_BOUNDARY = re.compile(r"(?<=[a-z0-9])(?=[A-Z])")
SENSITIVE_KEY_SUFFIXES = (
    "api_key",
    "access_token",
    "refresh_token",
    "token",
    "secret",
    "password",
    "authorization",
    "cookie",
    "session_id",
    "thread_id",
    "conversation_id",
    "client_secret",
    "private_key",
)
ASSIGNED_FIELD = re.compile(
    r"(?i)(?<![A-Za-z0-9_.-])"
    r"(?P<prefix>(?P<key_quote>[\"']?)(?P<key>[A-Za-z][A-Za-z0-9_.-]*)"
    r"(?P=key_quote)(?P<separator>\s*[:=]\s*))"
    r"(?P<value>\"(?:\\.|[^\"\\])*\"|'(?:\\.|[^'\\])*'|"
    r"(?:Basic|Bearer|Token)\s+[^\s,;\"'`]+|[^\s,;\"'`]+)"
)
LINE_SECRET_FIELD = re.compile(
    r"(?im)^(?P<prefix>\s*(?P<key>[A-Za-z][A-Za-z0-9_.-]*)\s*[:=]\s*)"
    r"(?P<value>[^\r\n]+)$"
)
MARKDOWN_HEADER_FIELD = re.compile(
    r"(?im)^(?P<prefix>\s*(?:(?:[-*+>]|[0-9]+[.)])\s+)+"
    r"(?:\[[ xX]\]\s+)?[*_]{0,2}"
    r"(?P<key>(?:Proxy-)?Authorization|(?:Set-)?Cookie)"
    r"[*_]{0,2}\s*:\s*)(?P<value>[^\r\n]+)$"
)
BACKTICK_HEADER_FIELD = re.compile(
    r"(?i)(?P<prefix>(?P<fence>`+)\s*"
    r"(?P<key>(?:Proxy-)?Authorization|(?:Set-)?Cookie)\s*:\s*)"
    r"(?P<value>[^\r\n`]*)(?P<suffix>(?P=fence))"
)
ANYWHERE_HEADER_FIELD = re.compile(
    r"(?im)^(?P<prefix>[^\r\n]*?(?<![A-Za-z0-9_.-`])"
    r"(?P<key>(?:Proxy-)?Authorization|(?:Set-)?Cookie)\s*:\s*)"
    r"(?P<value>[^\r\n]*)$"
)
MARKDOWN_TABLE_SECRET_FIELD = re.compile(
    r"(?im)(?P<prefix>\|\s*[*_`]{0,2}"
    r"(?P<key>(?:Proxy-)?Authorization|(?:Set-)?Cookie)"
    r"[*_`]{0,2}\s*\|\s*)(?P<value>[^|\r\n]*)(?P<suffix>\|)"
)
SPACED_SENSITIVE_FIELD = re.compile(
    r"(?i)(?<![A-Za-z0-9_.-])"
    r"(?P<prefix>(?P<key_quote>[\"']?)"
    r"(?P<label>(?:api|private)\s+key|client\s+secret|"
    r"(?:session|thread|conversation)\s+id)"
    r"(?P=key_quote)\s*[:=]\s*)"
    r"(?P<value>\"(?:\\.|[^\"\\])*\"|'(?:\\.|[^'\\])*'|"
    r"[^\r\n]+)"
)
SET_ENVIRONMENT_VARIABLE = re.compile(
    r"(?i)(?P<prefix>\[(?:System\.)?Environment\]::SetEnvironmentVariable"
    r"\(\s*(?P<key_quote>[\"'])(?P<key>[^\"']+)(?P=key_quote)\s*,\s*)"
    r"(?P<value>\"(?:\\.|[^\"\\])*\"|'(?:\\.|[^'\\])*')"
)
ENVIRON_ASSIGNMENT = re.compile(
    r"(?i)(?P<prefix>\b(?:os\.)?environ\s*\[\s*"
    r"(?P<key_quote>[\"'])(?P<key>[^\"']+)(?P=key_quote)\s*\]\s*=\s*)"
    r"(?P<value>\"(?:\\.|[^\"\\])*\"|'(?:\\.|[^'\\])*'|"
    r"[^\s,;]+)"
)
POWERSHELL_ENV_ASSIGNMENT = re.compile(
    r"(?i)(?P<prefix>\$env:(?P<key>[A-Za-z_][A-Za-z0-9_.-]*)\s*=\s*)"
    r"(?P<value>\"(?:\\.|[^\"\\])*\"|'(?:\\.|[^'\\])*'|"
    r"[^\s,;]+)"
)
TOKEN_SHAPE = re.compile(r"(?i)\b(?:sk|sess)-[A-Za-z0-9_-]{8,}\b")
AUTH_CREDENTIAL = re.compile(
    r"(?i)\b(Basic|Bearer|Token)(\s+)[A-Za-z0-9._~+/$=-]{4,}"
)
URI_USERINFO = re.compile(
    r"(?i)\b(?P<scheme>[A-Za-z][A-Za-z0-9+.-]*://)"
    r"(?P<userinfo>[^:@/\s]+:[^@/\s]+)@"
)
UNC_ABSOLUTE_PATH = re.compile(
    r"(?i)(?<![\w])(?:"
    r"\\\\(?:\?\\UNC\\)?[^\\/\s\r\n\"'`<>|]+\\"
    r"[^\\/\r\n\"'`<>|,;]+(?:\\[^\r\n\"'`<>|,;]*)?"
    r"|//(?:wsl\$|wsl\.localhost)/[^/\s\r\n\"'`<>|]+"
    r"(?:/[^\r\n\"'`<>|,;]*)?"
    r")"
)
PERSONAL_ABSOLUTE_PATH = re.compile(
    r"(?i)(?<![\w])(?:[A-Z]:[\\/](?:Users|Documents and Settings)[\\/]"
    r"|/(?:Users|home|tmp|root)/|/var/folders/|/private/var/|/mnt/[a-z]/)"
    r"[^\r\n\"'`<>]*"
)
WINDOWS_ABSOLUTE_PATH = re.compile(
    r"(?i)(?<![\w])[A-Z]:[\\/][^\r\n\"'`<>|,;]*"
)
ALLOWED_EVIDENCE_KINDS = {"test", "diff", "failure", "log"}
PUBLIC_RUN_FIELDS = {
    "run_id",
    "started_at",
    "finished_at",
    "wall_seconds",
    "codex_exit_code",
    "timed_out",
    "working_directory",
    "model",
    "reasoning_effort",
    "profile",
    "sandbox",
    "approval_policy",
    "ignore_user_config",
    "request_sha256",
}
PUBLIC_SUMMARY_FIELDS = {
    "json_objects",
    "invalid_json_lines",
    "parse_status",
    "execution_status",
    "codex_exit_code",
    "event_counts",
    "item_counts",
    "usage",
    "usage_source",
    "command_execution_count",
    "file_change_count",
    "mcp_call_count",
    "mcp_calls",
    "turn_completed_count",
    "turn_failed_count",
}


def normalize_key(key: str) -> str:
    with_boundaries = CAMEL_CASE_BOUNDARY.sub("_", key)
    return re.sub(r"[^A-Za-z0-9]+", "_", with_boundaries).strip("_").lower()


def is_sensitive_key(key: str) -> bool:
    normalized = normalize_key(key)
    return any(
        normalized == suffix or normalized.endswith(f"_{suffix}")
        for suffix in SENSITIVE_KEY_SUFFIXES
    )


def _redact_assigned_field(match: re.Match[str]) -> str:
    if not is_sensitive_key(match.group("key")):
        return match.group(0)
    value = match.group("value")
    is_quoted = len(value) >= 2 and value[0] in "\"'" and value[-1] == value[0]
    quote = value[0] if is_quoted else ""
    return f"{match.group('prefix')}{quote}[REDACTED]{quote}"


def _is_line_secret_key(key: str) -> bool:
    normalized = normalize_key(key)
    return is_sensitive_key(key) and normalized.endswith(
        ("authorization", "cookie")
    )


def _redact_line_secret_field(match: re.Match[str]) -> str:
    if not _is_line_secret_key(match.group("key")):
        return match.group(0)
    value = match.group("value").strip()
    is_quoted = len(value) >= 2 and value[0] in "\"'" and value[-1] == value[0]
    quote = value[0] if is_quoted else ""
    return f"{match.group('prefix')}{quote}[REDACTED]{quote}"


def _redact_backtick_header_field(match: re.Match[str]) -> str:
    return f"{match.group('prefix')}[REDACTED]{match.group('suffix')}"


def _redact_table_secret_field(match: re.Match[str]) -> str:
    return f"{match.group('prefix')}[REDACTED]{match.group('suffix')}"


def _redact_spaced_sensitive_field(match: re.Match[str]) -> str:
    value = match.group("value")
    is_quoted = len(value) >= 2 and value[0] in "\"'" and value[-1] == value[0]
    quote = value[0] if is_quoted else ""
    return f"{match.group('prefix')}{quote}[REDACTED]{quote}"


def _redact_syntax_assignment(match: re.Match[str]) -> str:
    if not is_sensitive_key(match.group("key")):
        return match.group(0)
    value = match.group("value")
    quote = value[0] if value[0] in "\"'" else ""
    return f"{match.group('prefix')}{quote}[REDACTED]{quote}"


def _is_redacted_value(value: str) -> bool:
    return bool(
        re.fullmatch(r"\s*[\"']?\[REDACTED\][\"']?\s*,?\s*", value)
    )


def assert_sanitized_content(
    text: str,
    *,
    private_paths: Iterable[Path] = (),
) -> None:
    rendered_paths = {
        variant
        for path in private_paths
        for variant in (
            str(path.resolve()),
            str(path.resolve()).replace("\\", "/"),
            str(path.resolve()).replace("/", "\\"),
        )
    }
    if any(
        rendered and rendered.lower() in text.lower()
        for rendered in rendered_paths
    ):
        raise ValueError("Private absolute path remained after sanitization")
    for pattern in (
        BACKTICK_HEADER_FIELD,
        MARKDOWN_TABLE_SECRET_FIELD,
        ANYWHERE_HEADER_FIELD,
        MARKDOWN_HEADER_FIELD,
        LINE_SECRET_FIELD,
    ):
        for match in pattern.finditer(text):
            if _is_line_secret_key(match.group("key")) and not _is_redacted_value(
                match.group("value")
            ):
                raise ValueError(
                    "Authorization or Cookie value remained after sanitization"
                )
    for match in SPACED_SENSITIVE_FIELD.finditer(text):
        if not _is_redacted_value(match.group("value")):
            raise ValueError("Spaced sensitive label remained after sanitization")
    for pattern in (
        SET_ENVIRONMENT_VARIABLE,
        ENVIRON_ASSIGNMENT,
        POWERSHELL_ENV_ASSIGNMENT,
    ):
        for match in pattern.finditer(text):
            if is_sensitive_key(match.group("key")) and not _is_redacted_value(
                match.group("value")
            ):
                raise ValueError("Environment secret remained after sanitization")
    for match in ASSIGNED_FIELD.finditer(text):
        if is_sensitive_key(match.group("key")) and not _is_redacted_value(
            match.group("value")
        ):
            raise ValueError("Sensitive assigned value remained after sanitization")
    residual_patterns = (
        TOKEN_SHAPE,
        AUTH_CREDENTIAL,
        UNC_ABSOLUTE_PATH,
        PERSONAL_ABSOLUTE_PATH,
        WINDOWS_ABSOLUTE_PATH,
        URI_USERINFO,
    )
    if any(pattern.search(text) for pattern in residual_patterns):
        raise ValueError("Secret or private path remained after sanitization")


def sanitize_text(text: str, *, private_paths: Iterable[Path] = ()) -> str:
    sanitized = text
    rendered_paths = sorted(
        {
            variant
            for path in private_paths
            for variant in (
                str(path.resolve()),
                str(path.resolve()).replace("\\", "/"),
                str(path.resolve()).replace("/", "\\"),
            )
        },
        key=len,
        reverse=True,
    )
    for rendered in rendered_paths:
        sanitized = re.sub(re.escape(rendered), "<PRIVATE_PATH>", sanitized, flags=re.IGNORECASE)
    sanitized = BACKTICK_HEADER_FIELD.sub(
        _redact_backtick_header_field,
        sanitized,
    )
    sanitized = MARKDOWN_TABLE_SECRET_FIELD.sub(
        _redact_table_secret_field,
        sanitized,
    )
    sanitized = ANYWHERE_HEADER_FIELD.sub(_redact_line_secret_field, sanitized)
    sanitized = MARKDOWN_HEADER_FIELD.sub(_redact_line_secret_field, sanitized)
    sanitized = LINE_SECRET_FIELD.sub(_redact_line_secret_field, sanitized)
    sanitized = SPACED_SENSITIVE_FIELD.sub(
        _redact_spaced_sensitive_field,
        sanitized,
    )
    sanitized = SET_ENVIRONMENT_VARIABLE.sub(_redact_syntax_assignment, sanitized)
    sanitized = ENVIRON_ASSIGNMENT.sub(_redact_syntax_assignment, sanitized)
    sanitized = POWERSHELL_ENV_ASSIGNMENT.sub(_redact_syntax_assignment, sanitized)
    sanitized = ASSIGNED_FIELD.sub(_redact_assigned_field, sanitized)
    sanitized = AUTH_CREDENTIAL.sub(
        lambda match: f"{match.group(1)}{match.group(2)}[REDACTED]",
        sanitized,
    )
    sanitized = TOKEN_SHAPE.sub("[REDACTED]", sanitized)
    sanitized = UNC_ABSOLUTE_PATH.sub("<PRIVATE_PATH>", sanitized)
    sanitized = PERSONAL_ABSOLUTE_PATH.sub("<PRIVATE_PATH>", sanitized)
    sanitized = WINDOWS_ABSOLUTE_PATH.sub("<PRIVATE_PATH>", sanitized)
    sanitized = URI_USERINFO.sub(
        lambda match: f"{match.group('scheme')}[REDACTED]@",
        sanitized,
    )
    return sanitized


def sanitize_value(value: Any, *, private_paths: Iterable[Path] = ()) -> Any:
    if isinstance(value, dict):
        return {
            str(key): (
                "[REDACTED]"
                if is_sensitive_key(str(key))
                else sanitize_value(child, private_paths=private_paths)
            )
            for key, child in value.items()
        }
    if isinstance(value, list):
        return [sanitize_value(child, private_paths=private_paths) for child in value]
    if isinstance(value, str):
        return sanitize_text(value, private_paths=private_paths)
    return value


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(value, dict):
        raise ValueError(f"Expected a JSON object: {path.name}")
    return value


def sanitized_file_text(path: Path, *, private_paths: Iterable[Path]) -> str:
    text = path.read_text(encoding="utf-8", errors="strict")
    if path.suffix.lower() == ".json":
        value = json.loads(text)
        return json.dumps(
            sanitize_value(value, private_paths=private_paths),
            ensure_ascii=False,
            indent=2,
        ) + "\n"
    if path.suffix.lower() == ".jsonl":
        lines: list[str] = []
        for raw in text.splitlines():
            if not raw.strip():
                continue
            try:
                value = json.loads(raw)
            except json.JSONDecodeError:
                lines.append(sanitize_text(raw, private_paths=private_paths))
            else:
                lines.append(
                    json.dumps(
                        sanitize_value(value, private_paths=private_paths),
                        ensure_ascii=False,
                    )
                )
        return "\n".join(lines) + ("\n" if lines else "")
    return sanitize_text(text, private_paths=private_paths)


def parse_evidence(values: Iterable[str]) -> list[tuple[str, Path]]:
    parsed: list[tuple[str, Path]] = []
    for value in values:
        kind, separator, raw_path = value.partition("=")
        if not separator or kind not in ALLOWED_EVIDENCE_KINDS or not raw_path:
            allowed = ", ".join(sorted(ALLOWED_EVIDENCE_KINDS))
            raise ValueError(f"Evidence must use KIND=PATH where KIND is one of: {allowed}")
        path = Path(raw_path)
        if not path.is_file():
            raise FileNotFoundError(f"Evidence file not found: {path}")
        parsed.append((kind, path))
    return parsed


def public_files(
    *,
    raw_directory: Path,
    repo_root: Path,
    evidence: Iterable[tuple[str, Path]],
) -> dict[Path, str]:
    request_path = require_raw_file(raw_directory, "request.md")
    events_path = require_raw_file(raw_directory, "events.jsonl")
    run_path = require_raw_file(raw_directory, "run.json")
    environment_path = require_raw_file(raw_directory, "environment.json")

    private_paths = (repo_root, raw_directory)
    summary = summarize(events_path, run_path)
    final_message = summary.get("final_agent_message")
    if not isinstance(final_message, str) or not final_message.strip():
        raise ValueError("Raw events do not contain a final agent message")
    run_metadata = read_json(run_path)
    environment = read_json(environment_path)
    metadata = {
        "run": {
            key: run_metadata[key]
            for key in PUBLIC_RUN_FIELDS
            if key in run_metadata
        },
        "environment": environment,
        "summary": {
            key: summary[key]
            for key in PUBLIC_SUMMARY_FIELDS
            if key in summary
        },
    }
    files = {
        Path("request.md"): sanitize_text(
            request_path.read_text(encoding="utf-8"), private_paths=private_paths
        ),
        Path("response.md"): sanitize_text(final_message, private_paths=private_paths),
        Path("run.json"): json.dumps(
            sanitize_value(metadata, private_paths=private_paths),
            ensure_ascii=False,
            indent=2,
        ) + "\n",
    }
    used_destinations: set[Path] = set(files)
    for kind, source in evidence:
        destination = Path("evidence") / f"{kind}-{source.name}"
        if destination in used_destinations:
            raise ValueError(f"Duplicate public evidence filename: {destination.name}")
        used_destinations.add(destination)
        files[destination] = sanitized_file_text(
            source, private_paths=(*private_paths, source.parent)
        )
    return files


def write_staged_files(
    staging_directory: Path,
    files: dict[Path, str],
) -> list[Path]:
    written: list[Path] = []
    for relative, content in sorted(files.items(), key=lambda item: item[0].as_posix()):
        destination = staging_directory / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(content, encoding="utf-8")
        written.append(relative)
    return written


def export_run(
    *,
    raw_directory: Path,
    public_directory: Path,
    repo_root: Path,
    evidence: Iterable[tuple[str, Path]] = (),
) -> list[Path]:
    layout = validate_run_pair(
        repo_root=repo_root,
        raw_directory=raw_directory,
        public_directory=public_directory,
    )
    root = layout.repo_root
    raw = layout.raw_directory
    public = layout.public_directory
    if not raw.is_dir():
        raise NotADirectoryError(f"Raw directory not found: {raw_directory}")
    if public.exists():
        raise FileExistsError(f"Public directory already exists: {public_directory}")

    evidence_list: list[tuple[str, Path]] = []
    for kind, source in evidence:
        if not source.is_file():
            raise FileNotFoundError(f"Evidence file not found: {source}")
        resolved_source = source.resolve(strict=True)
        if not is_within(resolved_source, root):
            raise ValueError("Evidence files must be inside the learning repository")
        if is_within(source.parent, raw) or is_within(resolved_source, raw):
            raise ValueError("Evidence files must not be inside the raw run directory")
        evidence_list.append((kind, resolved_source))

    files = public_files(
        raw_directory=raw,
        repo_root=root,
        evidence=evidence_list,
    )
    for destination, content in files.items():
        try:
            assert_sanitized_content(content, private_paths=(root, raw))
        except ValueError as exc:
            raise ValueError(f"Unsafe public content in {destination}: {exc}") from exc

    public.parent.mkdir(parents=True, exist_ok=True)
    scratch = validate_scratch_directory(layout)
    scratch.mkdir(parents=True, exist_ok=True)
    scratch = validate_scratch_directory(layout)
    staging = Path(
        tempfile.mkdtemp(prefix=f".export-{layout.run_id}-", dir=scratch)
    )
    try:
        relative_paths = write_staged_files(staging, files)
        if public.exists():
            raise FileExistsError(f"Public directory already exists: {public_directory}")
        staging.rename(public)
    except BaseException:
        shutil.rmtree(staging, ignore_errors=True)
        raise
    return [public / relative for relative in relative_paths]


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Export a sanitized public run. Raw JSONL and stderr remain in .local/raw."
        )
    )
    parser.add_argument("--raw-directory", type=Path, required=True)
    parser.add_argument("--public-directory", type=Path, required=True)
    parser.add_argument("--repo-root", type=Path, required=True)
    parser.add_argument(
        "--evidence",
        action="append",
        default=[],
        metavar="KIND=PATH",
        help="Repeat for test, diff, failure, or sanitized log evidence.",
    )
    args = parser.parse_args()
    try:
        evidence = parse_evidence(args.evidence)
        written = export_run(
            raw_directory=args.raw_directory,
            public_directory=args.public_directory,
            repo_root=args.repo_root,
            evidence=evidence,
        )
    except (
        FileNotFoundError,
        FileExistsError,
        NotADirectoryError,
        UnicodeDecodeError,
        json.JSONDecodeError,
        ValueError,
    ) as exc:
        parser.error(str(exc))
    print(json.dumps([path.name for path in written], ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
