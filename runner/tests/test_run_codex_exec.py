from __future__ import annotations

import unittest

from runner.run_codex_exec import build_command


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


if __name__ == "__main__":
    unittest.main()
