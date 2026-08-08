from __future__ import annotations

import io
import json
import tempfile
import unittest
from contextlib import redirect_stderr
from pathlib import Path
from unittest.mock import patch

from runner.run_codex_exec import build_command, run, validate_locations


class BuildCommandTests(unittest.TestCase):
    def test_pins_reproducibility_options_and_explicit_cwd(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            work = Path(temp)
            command = build_command(
                working_directory=work,
                model="test-model",
                reasoning="high",
                profile="benchmark",
                sandbox="read-only",
                approval_policy="never",
                ignore_user_config=True,
            )
        self.assertIn("--cd", command)
        self.assertIn(str(work.resolve()), command)
        self.assertIn("--model", command)
        self.assertIn("test-model", command)
        self.assertIn('model_reasoning_effort="high"', command)
        self.assertIn('approval_policy="never"', command)
        self.assertIn("--profile", command)
        self.assertIn("benchmark", command)
        self.assertIn("--ignore-user-config", command)
        self.assertEqual(command[-1], "-")

    def test_profile_and_user_config_are_optional(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            command = build_command(
                working_directory=Path(temp),
                model="test-model",
                reasoning="medium",
                profile=None,
                sandbox="read-only",
                approval_policy="on-request",
                ignore_user_config=False,
            )
        self.assertNotIn("--profile", command)
        self.assertNotIn("--ignore-user-config", command)

    def test_run_sends_utf8_and_writes_only_raw_output(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "repo"
            prompt = root / "week01-codex-basics" / "prompts" / "request.md"
            work = root / "week01-codex-basics" / "lab"
            output = root / "week01-codex-basics" / ".local" / "raw" / "run-a"
            prompt.parent.mkdir(parents=True)
            work.mkdir(parents=True)
            prompt.write_text("한국어 프롬프트", encoding="utf-8")
            completed = type("Completed", (), {"returncode": 0})()

            with redirect_stderr(io.StringIO()), patch(
                "runner.run_codex_exec.find_git_root", return_value=root.resolve()
            ), patch(
                "runner.run_codex_exec.build_report",
                return_value={"working_directory": "week01-codex-basics/lab"},
            ), patch("runner.run_codex_exec.subprocess.run", return_value=completed) as mocked:
                result = run(
                    prompt_file=prompt,
                    output_directory=output,
                    working_directory=work,
                    model="test-model",
                    reasoning="medium",
                    profile=None,
                    sandbox="read-only",
                    approval_policy="never",
                    timeout_seconds=10,
                    ignore_user_config=False,
                )

            self.assertEqual(result, 0)
            self.assertEqual(mocked.call_args.kwargs["input"], "한국어 프롬프트".encode("utf-8"))
            self.assertEqual(mocked.call_args.kwargs["cwd"], work.resolve())
            self.assertEqual(
                {path.name for path in output.iterdir()},
                {
                    "environment.json",
                    "events.jsonl",
                    "request.md",
                    "run.json",
                    "stderr.log",
                    "summary.json",
                },
            )
            self.assertFalse((work / "run.json").exists())
            metadata = json.loads((output / "run.json").read_text(encoding="utf-8"))
            self.assertEqual(metadata["run_id"], "run-a")
            self.assertEqual(len(metadata["request_sha256"]), 64)

    def test_dry_run_creates_no_output_and_invokes_no_codex(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "repo"
            prompt = root / "week01-codex-basics" / "prompts" / "request.md"
            work = root / "week01-codex-basics" / "lab"
            output = root / "week01-codex-basics" / ".local" / "raw" / "preview"
            prompt.parent.mkdir(parents=True)
            work.mkdir(parents=True)
            prompt.write_text("preview", encoding="utf-8")
            preview = io.StringIO()
            with redirect_stderr(preview), patch(
                "runner.run_codex_exec.find_git_root", return_value=root.resolve()
            ), patch("runner.run_codex_exec.subprocess.run") as mocked:
                result = run(
                    prompt_file=prompt,
                    output_directory=output,
                    working_directory=work,
                    model="test-model",
                    reasoning="medium",
                    profile=None,
                    sandbox="read-only",
                    approval_policy="never",
                    timeout_seconds=10,
                    ignore_user_config=False,
                    dry_run=True,
                )
            self.assertEqual(result, 0)
            mocked.assert_not_called()
            self.assertFalse(output.exists())
            payload = json.loads(preview.getvalue())
            self.assertEqual(len(payload["request_sha256"]), 64)
            self.assertEqual(payload["working_directory"], str(work.resolve()))

    def test_root_overlap_and_malformed_raw_paths_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "repo"
            week = root / "week01-codex-basics"
            work = week / "lab"
            shared_work = root / "shared" / "lab"
            work.mkdir(parents=True)
            shared_work.mkdir(parents=True)
            output = week / ".local" / "raw" / "run"
            with self.assertRaisesRegex(ValueError, "repository root"):
                validate_locations(
                    repo_root=root,
                    working_directory=root,
                    output_directory=output,
                )
            validate_locations(
                repo_root=root,
                working_directory=shared_work,
                output_directory=output,
            )
            with self.assertRaisesRegex(ValueError, "must not overlap"):
                validate_locations(
                    repo_root=root,
                    working_directory=week,
                    output_directory=output,
                )
            with self.assertRaisesRegex(ValueError, "exactly"):
                validate_locations(
                    repo_root=root,
                    working_directory=work,
                    output_directory=work / ".local" / "raw" / "run",
                )


if __name__ == "__main__":
    unittest.main()
