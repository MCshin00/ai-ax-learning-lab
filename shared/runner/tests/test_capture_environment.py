from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from runner.capture_environment import discovery_layers, tracked_config_files


class CaptureEnvironmentTests(unittest.TestCase):
    def test_collects_codex_configuration_from_repo_root_through_cwd(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "repo"
            cwd = root / "week05" / "run-a"
            sibling = root / "week02"
            for directory in (
                root,
                cwd / ".codex",
                root / ".agents" / "skills" / "root-skill",
                root / "week05" / ".agents" / "skills" / "week-skill" / "scripts",
                root / "week05" / ".agents" / "skills" / "week-skill" / "scripts" / "__pycache__",
                root / "week05" / ".agents" / "skills" / "week-skill" / "references",
                root / "week05" / ".codex" / "hooks",
                sibling / ".agents" / "skills" / "sibling-skill",
            ):
                directory.mkdir(parents=True, exist_ok=True)

            (root / "AGENTS.md").write_text("root", encoding="utf-8")
            (root / ".agents" / "skills" / "root-skill" / "SKILL.md").write_text(
                "root skill", encoding="utf-8"
            )
            (root / "week05" / ".agents" / "skills" / "week-skill" / "SKILL.md").write_text(
                "week skill", encoding="utf-8"
            )
            (root / "week05" / ".agents" / "skills" / "week-skill" / "scripts" / "check.py").write_text(
                "print('check')", encoding="utf-8"
            )
            (root / "week05" / ".agents" / "skills" / "week-skill" / "scripts" / "__pycache__" / "check.pyc").write_bytes(
                b"cache"
            )
            (root / "week05" / ".agents" / "skills" / "week-skill" / "references" / "policy.md").write_text(
                "policy", encoding="utf-8"
            )
            (root / "week05" / ".codex" / "hooks" / "stop.py").write_text(
                "print('stop')", encoding="utf-8"
            )
            (cwd / ".codex" / "config.toml").write_text("[features]", encoding="utf-8")
            (sibling / ".agents" / "skills" / "sibling-skill" / "SKILL.md").write_text(
                "sibling skill", encoding="utf-8"
            )

            resolved_root = root.resolve()
            resolved_cwd = cwd.resolve()
            self.assertEqual(
                discovery_layers(root, cwd),
                [resolved_root, resolved_root / "week05", resolved_cwd],
            )
            relative = {
                path.relative_to(resolved_root).as_posix()
                for path in tracked_config_files(root, cwd)
            }
            self.assertEqual(
                relative,
                {
                    "AGENTS.md",
                    ".agents/skills/root-skill/SKILL.md",
                    "week05/.agents/skills/week-skill/SKILL.md",
                    "week05/.agents/skills/week-skill/references/policy.md",
                    "week05/.agents/skills/week-skill/scripts/check.py",
                    "week05/.codex/hooks/stop.py",
                    "week05/run-a/.codex/config.toml",
                },
            )


if __name__ == "__main__":
    unittest.main()
