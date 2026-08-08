from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from runner import path_contract
from runner.export_public_run import (
    assert_sanitized_content,
    export_run,
    parse_evidence,
    sanitize_text,
    sanitize_value,
)


class ExportPublicRunTests(unittest.TestCase):
    def create_raw_run(
        self,
        base: Path,
        *,
        run_id: str = "run-a",
    ) -> tuple[Path, Path, Path, Path]:
        root = base / "personal" / "repo"
        week = root / "week01-codex-basics"
        raw = week / ".local" / "raw" / run_id
        public = week / "runs" / run_id
        raw.mkdir(parents=True)
        (raw / "request.md").write_text(
            f"Inspect {week / 'lab'}\nOPENAI_API_KEY=sk-abcdefghijk",
            encoding="utf-8",
        )
        events = [
            {"type": "thread.started", "thread_id": "thread-secret"},
            {
                "type": "item.completed",
                "item": {
                    "type": "agent_message",
                    "text": (
                        f"Done at {week / 'lab'}; "
                        "OPENAI_SESSION_ID=session-secret; Bearer abc.def.ghi"
                    ),
                },
            },
            {
                "type": "turn.completed",
                "usage": {"input_tokens": 10, "output_tokens": 2},
            },
        ]
        (raw / "events.jsonl").write_text(
            "\n".join(json.dumps(event) for event in events),
            encoding="utf-8",
        )
        (raw / "stderr.log").write_text("private raw log", encoding="utf-8")
        (raw / "run.json").write_text(
            json.dumps(
                {
                    "run_id": run_id,
                    "model": "test-model",
                    "reasoning_effort": "medium",
                    "working_directory": "week01-codex-basics/lab",
                    "codex_exit_code": 0,
                }
            ),
            encoding="utf-8",
        )
        (raw / "environment.json").write_text(
            json.dumps(
                {
                    "working_directory": "week01-codex-basics/lab",
                    "harness_file_sha256": {},
                }
            ),
            encoding="utf-8",
        )
        return root, week, raw, public

    def test_sanitize_text_redacts_prefixed_and_quoted_secrets(self) -> None:
        secrets = (
            "openai-secret",
            "my-secret",
            "my-password",
            "thread-secret",
            "session-secret",
            "basic-credential",
            "bearer-credential",
            "token-credential",
        )
        rendered = sanitize_text(
            "\n".join(
                (
                    'OPENAI_API_KEY="openai-secret"',
                    "MY_SECRET='my-secret'",
                    'MY_PASSWORD = "my-password"',
                    "CODEX_THREAD_ID=thread-secret",
                    "'OPENAI_SESSION_ID': 'session-secret'",
                    "Authorization: Basic basic-credential",
                    'authorization = "Bearer bearer-credential"',
                    '"AUTHORIZATION": "Token token-credential"',
                )
            )
        )

        for secret in secrets:
            self.assertNotIn(secret, rendered)
        self.assertEqual(rendered.count("[REDACTED]"), len(secrets))
        self.assertIn('OPENAI_API_KEY="[REDACTED]"', rendered)
        self.assertIn("Authorization: [REDACTED]", rendered)
        self.assertNotRegex(rendered, r"(?i)Authorization\s*[:=].*(?:Basic|Bearer|Token)")

    def test_sanitize_text_redacts_all_values_in_cookie_headers(self) -> None:
        rendered = sanitize_text(
            "\n".join(
                (
                    "Cookie: session=one; preference=two; auth=three",
                    'HTTP_COOKIE = "first=four; second=five"',
                    "Set-Cookie: access=six; HttpOnly; Secure",
                )
            )
        )

        for secret in ("one", "two", "three", "four", "five", "six"):
            self.assertNotIn(secret, rendered)
        for cookie_name in ("session=", "preference=", "auth=", "first=", "second="):
            self.assertNotIn(cookie_name, rendered)
        self.assertEqual(rendered.count("[REDACTED]"), 3)

    def test_sanitize_text_redacts_authorization_for_any_scheme(self) -> None:
        rendered = sanitize_text(
            "\n".join(
                (
                    "Authorization: ApiKey topsecret12345",
                    'Authorization: Digest username="alice", response="topsecret12345"',
                    "PROXY_AUTHORIZATION = Custom opaque-secret-value",
                    "Proxy-Authorization: NTLM ntlm-secret-value",
                )
            )
        )

        self.assertEqual(rendered.count("[REDACTED]"), 4)
        for leaked in (
            "ApiKey",
            "Digest",
            "alice",
            "topsecret12345",
            "Custom",
            "opaque-secret-value",
            "NTLM",
            "ntlm-secret-value",
        ):
            self.assertNotIn(leaked, rendered)

    def test_sanitize_text_redacts_markdown_prefixed_and_backtick_headers(self) -> None:
        source_lines = (
            "- Authorization: ApiKey topsecret12345",
            '> Authorization: Digest username="alice", response="digestsecret123"',
            "* Cookie: a=one; b=two",
            "+ Proxy-Authorization: NTLM ntlmsecret123",
            "1. Set-Cookie: session=cookiesecret123; HttpOnly",
            "Use `Authorization: ApiKey inlinesecret123` only in examples.",
        )
        rendered = sanitize_text("\n".join(source_lines))

        for leaked in (
            "topsecret12345",
            "alice",
            "digestsecret123",
            "a=one",
            "b=two",
            "ntlmsecret123",
            "cookiesecret123",
            "inlinesecret123",
        ):
            self.assertNotIn(leaked, rendered)
        self.assertEqual(rendered.count("[REDACTED]"), len(source_lines))
        self.assertIn("- Authorization: [REDACTED]", rendered)
        self.assertIn("> Authorization: [REDACTED]", rendered)
        self.assertIn("* Cookie: [REDACTED]", rendered)
        self.assertIn("Use `Authorization: [REDACTED]` only in examples.", rendered)

    def test_guard_rejects_markdown_headers_and_accepts_their_sanitized_forms(self) -> None:
        unsafe_values = (
            "- Authorization: ApiKey topsecret12345",
            '> Authorization: Digest username="alice", response="digestsecret123"',
            "* Cookie: a=one; b=two",
            "`Proxy-Authorization: NTLM ntlmsecret123`",
            "Text before `Set-Cookie: session=cookiesecret123; HttpOnly` after.",
        )

        for original in unsafe_values:
            with self.subTest(original=original):
                with self.assertRaises(ValueError):
                    assert_sanitized_content(original)
                sanitized = sanitize_text(original)
                self.assertNotEqual(sanitized, original)
                assert_sanitized_content(sanitized)

        assert_sanitized_content("- authorization_policy: never")

    def test_sanitize_text_redacts_log_prefixed_headers_and_markdown_tables(self) -> None:
        source_lines = (
            "[INFO] Authorization: ApiKey log-secret-123",
            "[DEBUG] Cookie: a=one; b=two",
            "Header Authorization: Digest username=alice, response=header-secret-123",
            "| Authorization | ApiKey table-secret-123 |",
            "| Header | Cookie | session=table-cookie-secret; preference=dark |",
        )
        rendered = sanitize_text("\n".join(source_lines))

        for leaked in (
            "log-secret-123",
            "a=one",
            "b=two",
            "alice",
            "header-secret-123",
            "table-secret-123",
            "table-cookie-secret",
            "preference=dark",
        ):
            self.assertNotIn(leaked, rendered)
        self.assertEqual(rendered.count("[REDACTED]"), len(source_lines))
        self.assertIn("[INFO] Authorization: [REDACTED]", rendered)
        self.assertIn("[DEBUG] Cookie: [REDACTED]", rendered)
        self.assertIn("| Authorization | [REDACTED]|", rendered)
        self.assertIn("| Header | Cookie | [REDACTED]|", rendered)

    def test_guard_rejects_log_and_table_headers_but_keeps_benign_context(self) -> None:
        unsafe_values = (
            "[INFO] Authorization: ApiKey log-secret-123",
            "[DEBUG] Cookie: a=one; b=two",
            "Header Proxy-Authorization: NTLM proxy-log-secret",
            "| Authorization | ApiKey table-secret-123 |",
            "| Header | Set-Cookie | session=table-cookie-secret |",
        )

        for original in unsafe_values:
            with self.subTest(original=original):
                with self.assertRaises(ValueError):
                    assert_sanitized_content(original)
                sanitized = sanitize_text(original)
                self.assertNotEqual(sanitized, original)
                assert_sanitized_content(sanitized)

        benign = "\n".join(
            (
                "[INFO] authorization_policy: never",
                "Header authorization behavior: documented",
                "| Authorization policy | never |",
                "| Header | Value |",
                "This sentence mentions Authorization without a colon.",
            )
        )
        self.assertEqual(sanitize_text(benign), benign)
        assert_sanitized_content(benign)

    def test_sanitize_text_redacts_spaced_session_and_thread_labels(self) -> None:
        rendered = sanitize_text(
            "\n".join(
                (
                    "session id: session-secret-value",
                    '"thread id" = "thread-secret-value"',
                    "conversation id: conversation-secret-value",
                )
            )
        )

        self.assertEqual(rendered.count("[REDACTED]"), 3)
        self.assertNotIn("session-secret-value", rendered)
        self.assertNotIn("thread-secret-value", rendered)
        self.assertNotIn("conversation-secret-value", rendered)

    def test_sanitize_text_redacts_environment_syntax_and_spaced_key_labels(self) -> None:
        secrets = (
            "powershellsecret123",
            "envsecret123",
            "pythonsecret123",
            "spacedapikey123",
            "spacedprivatekey123",
        )
        rendered = sanitize_text(
            "\n".join(
                (
                    "$env:OPENAI_API_KEY='powershellsecret123'",
                    "[Environment]::SetEnvironmentVariable('OPENAI_API_KEY','envsecret123')",
                    'os.environ["OPENAI_API_KEY"] = "pythonsecret123"',
                    "api key: spacedapikey123",
                    "private key: spacedprivatekey123",
                )
            )
        )

        for secret in secrets:
            self.assertNotIn(secret, rendered)
        self.assertEqual(rendered.count("[REDACTED]"), len(secrets))

    def test_sanitize_text_redacts_url_and_database_uri_userinfo(self) -> None:
        rendered = sanitize_text(
            "\n".join(
                (
                    "DATABASE_URL=postgresql://dbuser:dbpass123@db.example.test/app",
                    "url=https://alice:webpass123@example.test/private",
                )
            )
        )

        self.assertNotIn("dbuser:dbpass123", rendered)
        self.assertNotIn("alice:webpass123", rendered)
        self.assertIn("postgresql://[REDACTED]@db.example.test/app", rendered)
        self.assertIn("https://[REDACTED]@example.test/private", rendered)

    def test_sanitize_value_redacts_required_json_key_variants(self) -> None:
        result = sanitize_value(
            {
                "nested": {
                    "x-api-key": "one",
                    "access-token": "one-b",
                    "clientSecret": "two",
                    "private_key": "three",
                    "OPENAI_API_KEY": "four",
                    "token_usage": {"input_tokens": 10},
                    "safe": "kept",
                }
            }
        )
        nested = result["nested"]
        for key in (
            "x-api-key",
            "access-token",
            "clientSecret",
            "private_key",
            "OPENAI_API_KEY",
        ):
            self.assertEqual(nested[key], "[REDACTED]")
        self.assertEqual(nested["token_usage"], {"input_tokens": 10})
        self.assertEqual(nested["safe"], "kept")

    def test_sanitize_text_redacts_unc_and_wsl_unc_paths(self) -> None:
        rendered = sanitize_text(
            "\n".join(
                (
                    r"server=\\fileserver\private-share\alice\report.txt",
                    r'wsl="\\wsl.localhost\Ubuntu\home\alice\repo\result.json"',
                    r"wsl-dollar=\\wsl$\Ubuntu\home\alice\repo",
                    r"forward=//wsl.localhost/Ubuntu/home/alice/repo/file.txt",
                    r'spaced="\\server\share\Project Folder\Alice\secret.txt"',
                    r'extended="\\?\UNC\server\share\Project Folder\Alice\secret.txt"',
                )
            )
        )

        self.assertNotIn("fileserver", rendered.lower())
        self.assertNotIn("wsl.localhost", rendered.lower())
        self.assertNotIn("wsl$", rendered.lower())
        self.assertNotIn("/home/alice", rendered.lower())
        self.assertNotIn("Project Folder", rendered)
        self.assertEqual(rendered.count("<PRIVATE_PATH>"), 6)

    def test_sanitize_text_redacts_additional_unix_and_spaced_windows_paths(self) -> None:
        rendered = sanitize_text(
            "\n".join(
                (
                    "root=/root/private/alice/result.json",
                    "mac=/var/folders/ab/private-cache/result.json",
                    "private=/private/var/tmp/alice/result.json",
                    r'windows="D:\Project Folder\Alice\secret.txt"',
                )
            )
        )

        for leaked in ("/root/private", "/var/folders", "/private/var", "Project Folder", "Alice"):
            self.assertNotIn(leaked, rendered)
        self.assertEqual(rendered.count("<PRIVATE_PATH>"), 4)

    def test_fail_closed_guard_rejects_original_variants_and_accepts_sanitized_text(self) -> None:
        unsafe_values = (
            "Authorization: ApiKey topsecret12345",
            'Authorization: Digest username="alice", response="topsecret12345"',
            "Proxy-Authorization: NTLM ntlm-secret-value",
            "$env:OPENAI_API_KEY='powershellsecret123'",
            "[Environment]::SetEnvironmentVariable('OPENAI_API_KEY','envsecret123')",
            'os.environ["OPENAI_API_KEY"] = "pythonsecret123"',
            "api key: spacedapikey123",
            "private key: spacedprivatekey123",
            "session id: session-secret-value",
            "thread id = thread-secret-value",
            "conversation id: conversation-secret-value",
            "postgresql://dbuser:dbpass123@db.example.test/app",
            "/root/private/alice/result.json",
            "/var/folders/ab/private-cache/result.json",
            "/private/var/tmp/alice/result.json",
            r"D:\Project Folder\Alice\secret.txt",
            r"\\server\share\Project Folder\Alice\secret.txt",
            r"\\?\UNC\server\share\Project Folder\Alice\secret.txt",
        )

        for original in unsafe_values:
            with self.subTest(original=original):
                with self.assertRaises(ValueError):
                    assert_sanitized_content(original)
                sanitized = sanitize_text(original)
                self.assertNotEqual(sanitized, original)
                assert_sanitized_content(sanitized)

        assert_sanitized_content(
            "authorization_policy: never\nsession id count: 2\nrelative/path.txt\n"
            '{"Authorization": "[REDACTED]", "safe": "kept"}'
        )

    def test_exports_only_sanitized_public_artifacts(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root, week, raw, public = self.create_raw_run(Path(temp))
            evidence = week / ".local" / "test-result.json"
            evidence.write_text(
                json.dumps(
                    {
                        "clientSecret": "secret",
                        "output": f"passed in {root}",
                    }
                ),
                encoding="utf-8",
            )

            written = export_run(
                raw_directory=raw,
                public_directory=public,
                repo_root=root,
                evidence=[("test", evidence)],
            )

            self.assertEqual(
                {path.relative_to(public.resolve()).as_posix() for path in written},
                {
                    "request.md",
                    "response.md",
                    "run.json",
                    "evidence/test-test-result.json",
                },
            )
            rendered = "\n".join(path.read_text(encoding="utf-8") for path in written)
            self.assertNotIn(str(root), rendered)
            self.assertNotIn("sk-abcdefghijk", rendered)
            self.assertNotIn("session-secret", rendered)
            self.assertNotIn("abc.def.ghi", rendered)
            self.assertNotIn("private raw log", rendered)
            self.assertIn("[REDACTED]", rendered)
            self.assertFalse((public / "events.jsonl").exists())
            self.assertFalse((public / "stderr.log").exists())
            self.assertEqual(list((week / ".local" / "scratch").iterdir()), [])

    def test_evidence_kind_is_explicit(self) -> None:
        with self.assertRaises(ValueError):
            parse_evidence(["unknown=result.txt"])

    def test_rejects_evidence_outside_the_learning_repository(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            root, _, raw, public = self.create_raw_run(base)
            outside = base / "outside.log"
            outside.write_text("not public\n", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "inside the learning repository"):
                export_run(
                    raw_directory=raw,
                    public_directory=public,
                    repo_root=root,
                    evidence=[("log", outside)],
                )

    def test_rejects_evidence_from_inside_raw_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root, _, raw, public = self.create_raw_run(Path(temp))
            evidence = raw / "raw-evidence.log"
            evidence.write_text("must stay private\n", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "must not be inside the raw"):
                export_run(
                    raw_directory=raw,
                    public_directory=public,
                    repo_root=root,
                    evidence=[("log", evidence)],
                )

    def test_rejects_each_symlinked_required_raw_file(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root, _, raw, public = self.create_raw_run(Path(temp))

            for filename in (
                "request.md",
                "events.jsonl",
                "run.json",
                "environment.json",
            ):
                with self.subTest(filename=filename), patch(
                    "runner.path_contract._required_file_is_symlink",
                    side_effect=lambda path, target=filename: path.name == target,
                ), self.assertRaisesRegex(ValueError, "must not be a symlink"):
                    export_run(
                        raw_directory=raw,
                        public_directory=public,
                        repo_root=root,
                    )

    def test_rejects_each_required_raw_file_resolving_outside_raw(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            root, _, raw, public = self.create_raw_run(base)
            outside = base / "outside-request.md"
            outside.write_text("outside\n", encoding="utf-8")
            original_resolver = path_contract._resolve_required_file

            for filename in (
                "request.md",
                "events.jsonl",
                "run.json",
                "environment.json",
            ):
                def resolve_required(path: Path, target: str = filename) -> Path:
                    if path.name == target:
                        return outside.resolve()
                    return original_resolver(path)

                with self.subTest(filename=filename), patch(
                    "runner.path_contract._resolve_required_file",
                    side_effect=resolve_required,
                ), self.assertRaisesRegex(ValueError, "resolve directly inside"):
                    export_run(
                        raw_directory=raw,
                        public_directory=public,
                        repo_root=root,
                    )

    def test_export_fails_closed_when_sanitizer_leaves_a_secret(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root, _, raw, public = self.create_raw_run(Path(temp))
            (raw / "request.md").write_text(
                "OPENAI_API_KEY=deliberate-leak",
                encoding="utf-8",
            )

            with patch(
                "runner.export_public_run.sanitize_text",
                side_effect=lambda text, **_: text,
            ), self.assertRaisesRegex(ValueError, "Sensitive assigned value"):
                export_run(
                    raw_directory=raw,
                    public_directory=public,
                    repo_root=root,
                )

            self.assertFalse(public.exists())

    def test_failed_staging_is_cleaned_and_export_can_be_retried(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root, week, raw, public = self.create_raw_run(Path(temp))
            staging_directories: list[Path] = []

            def fail_after_partial_write(staging: Path, _: object) -> list[Path]:
                staging_directories.append(staging)
                (staging / "partial.txt").write_text("partial", encoding="utf-8")
                raise OSError("simulated write failure")

            with patch(
                "runner.export_public_run.write_staged_files",
                side_effect=fail_after_partial_write,
            ), self.assertRaisesRegex(OSError, "simulated write failure"):
                export_run(
                    raw_directory=raw,
                    public_directory=public,
                    repo_root=root,
                )

            scratch = week / ".local" / "scratch"
            self.assertEqual(staging_directories[0].parent.resolve(), scratch.resolve())
            self.assertFalse(staging_directories[0].exists())
            self.assertFalse(public.exists())
            self.assertEqual(list(scratch.iterdir()), [])

            written = export_run(
                raw_directory=raw,
                public_directory=public,
                repo_root=root,
            )
            self.assertTrue(public.is_dir())
            self.assertEqual({path.name for path in written}, {"request.md", "response.md", "run.json"})


if __name__ == "__main__":
    unittest.main()
