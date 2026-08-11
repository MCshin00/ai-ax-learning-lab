from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "validate_contract.py"
SPEC = importlib.util.spec_from_file_location("task_contract_validator", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Cannot load validator: {SCRIPT}")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
validate = MODULE.validate


class ContractValidatorTests(unittest.TestCase):
    def test_complete_contract_is_valid(self) -> None:
        sections = {
            "Goal": "Implement a deterministic sample.",
            "Context": "The benchmark repository is the only context.",
            "Allowed paths": "- src/**\n- tests/**",
            "Forbidden changes": "- Do not delete tests.",
            "Acceptance criteria": "- The valid case passes.\n- The missing-heading case fails.",
            "Required verification": "Run `python -m unittest -v`.",
            "Stop conditions": "Stop if the contract conflicts with repository rules.",
            "Handoff": "Report changed files, tests, and remaining risks.",
        }
        body = "\n\n".join(f"## {name}\n\n{sections[name]}" for name in sections)
        path = Path(tempfile.mkdtemp()) / "TASK.md"
        path.write_text("# TASK\n\n" + body, encoding="utf-8")
        self.assertTrue(validate(path)["valid"])

    def test_missing_heading_is_invalid(self) -> None:
        path = Path(tempfile.mkdtemp()) / "TASK.md"
        path.write_text("# TASK\n\n## Goal\n\nvalue", encoding="utf-8")
        self.assertFalse(validate(path)["valid"])


if __name__ == "__main__":
    unittest.main()
