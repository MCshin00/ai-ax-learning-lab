from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from runner.run_codex_exec import build_command, run


class BuildCommandTests(unittest.TestCase):
    def test_pins_reproducibility_options(self) -> None:
        command = build_command(
            model="test-model",
            reasoning="high",
            profile="benchmark",
            sandbox="read-only",
            approval_policy="never",
            ignore_user_config=True,
        )
        self.assertIn("--model", command)
        self.assertIn("test-model", command)
        self.assertIn('model_reasoning_effort="high"', command)
        self.assertIn('approval_policy="never"', command)
        self.assertIn("--profile", command)
        self.assertIn("benchmark", command)
        self.assertIn("--ignore-user-config", command)
        self.assertEqual(command[-1], "-")

    def test_profile_and_user_config_are_optional(self) -> None:
        command = build_command(
            model="test-model",
            reasoning="medium",
            profile=None,
            sandbox="workspace-write",
            approval_policy="on-request",
            ignore_user_config=False,
        )
        self.assertNotIn("--profile", command)
        self.assertNotIn("--ignore-user-config", command)

    def test_run_sends_utf8_bytes_and_uses_requested_working_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            prompt = root / "prompt.md"
            prompt.write_text("한국어 프롬프트", encoding="utf-8")
            work = root / "work"
            work.mkdir()
            completed = type("Completed", (), {"returncode": 0})()

            with patch("runner.run_codex_exec.subprocess.run", return_value=completed) as mocked:
                result = run(
                    prompt_file=prompt,
                    events_file=root / "events.jsonl",
                    stderr_file=root / "stderr.log",
                    metadata_file=root / "run.json",
                    working_directory=work,
                    model="test-model",
                    reasoning="medium",
                    profile=None,
                    sandbox="workspace-write",
                    approval_policy="never",
                    timeout_seconds=10,
                    ignore_user_config=False,
                )

            self.assertEqual(result, 0)
            self.assertEqual(mocked.call_args.kwargs["input"], "한국어 프롬프트".encode("utf-8"))
            self.assertEqual(mocked.call_args.kwargs["cwd"], work.resolve())
            self.assertTrue((root / "run.json").is_file())


if __name__ == "__main__":
    unittest.main()
