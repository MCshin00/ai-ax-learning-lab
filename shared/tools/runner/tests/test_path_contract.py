from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from runner.path_contract import validate_raw_run_directory, validate_run_pair


class PathContractTests(unittest.TestCase):
    def test_accepts_only_the_matching_week_and_run_pair(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "repo"
            week = root / "week07-langchain-langgraph"
            raw = week / ".local" / "raw" / "memory-a"
            public = week / "runs" / "memory-a"
            root.mkdir()

            layout = validate_run_pair(
                repo_root=root,
                raw_directory=raw,
                public_directory=public,
            )

            self.assertEqual(layout.week_name, "week07-langchain-langgraph")
            self.assertEqual(layout.run_id, "memory-a")
            self.assertEqual(layout.raw_directory, raw.resolve())
            self.assertEqual(layout.public_directory, public.resolve())
            self.assertEqual(
                layout.scratch_directory,
                (week / ".local" / "scratch").resolve(),
            )

    def test_rejects_root_shared_and_lab_nested_raw_directories(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "repo"
            week = root / "week07-langchain-langgraph"
            root.mkdir()
            invalid = (
                root / ".local" / "raw" / "run-a",
                root / "shared" / ".local" / "raw" / "run-a",
                week / "lab" / ".local" / "raw" / "run-a",
                root / "week07" / ".local" / "raw" / "run-a",
                week / ".local" / "raw",
                week / ".local" / "raw" / "run-a" / "nested",
            )

            for raw in invalid:
                with self.subTest(raw=raw), self.assertRaisesRegex(ValueError, "exactly|weekNN-slug"):
                    validate_raw_run_directory(
                        repo_root=root,
                        raw_directory=raw,
                    )

    def test_rejects_nested_or_mismatched_public_directories(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "repo"
            week = root / "week07-langchain-langgraph"
            raw = week / ".local" / "raw" / "run-a"
            root.mkdir()
            invalid = (
                root / "runs" / "run-a",
                week / "lab" / "runs" / "run-a",
                week / "runs" / "run-a" / "nested",
                week / "runs" / "run-b",
                root / "week08-rag-evaluation" / "runs" / "run-a",
            )

            for public in invalid:
                with self.subTest(public=public), self.assertRaisesRegex(
                    ValueError,
                    "exactly|same week and run id",
                ):
                    validate_run_pair(
                        repo_root=root,
                        raw_directory=raw,
                        public_directory=public,
                    )


if __name__ == "__main__":
    unittest.main()
