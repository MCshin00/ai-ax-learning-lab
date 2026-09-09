"""Advanced draft behavior in temporary roots, including real process death."""
import asyncio
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from mcp import Client
from mcp.client.stdio import StdioServerParameters, stdio_client

from learning_lab_mcp.changes import DraftChanges
from learning_lab_mcp.drafts import DraftError, DraftStore
from learning_lab_mcp.inspector import create_inspector_server
from learning_lab_mcp.server import CATALOG, PurchaseItem, mcp, review_purchase


def quote(quantity=2, budget=8000):
    return review_purchase([
        PurchaseItem(product_id="NOTE-01", quantity=quantity),
        PurchaseItem(product_id="PEN-02", quantity=1),
    ], budget)


def seed(root):
    store = DraftStore(root)
    preview = store.preview("draft-001", quote(1))
    store.save(preview.preview_id)
    path = root / "draft-001.json"
    document = json.loads(path.read_bytes())
    document["manual_note"] = "Keep this note."
    # Intentional formatting demonstrates that undo restores exact original bytes.
    path.write_text(json.dumps(document, ensure_ascii=False, indent=4) + "\n", encoding="utf-8")
    return store, path


class ChangeTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name) / "drafts"
        self.store, self.path = seed(self.root)
        self.original = self.path.read_bytes()
        self.changes = DraftChanges(self.store)

    def preview(self, operation="edit-001", quantity=2, budget=8000):
        return self.changes.preview_edit("draft-001", operation, quote(quantity, budget))

    def apply_edit(self, operation="edit-001", quantity=2):
        return self.changes.apply(self.preview(operation, quantity).preview_id)

    def test_preview_preserves_notes_and_snapshot_without_creating_history(self):
        preview = self.preview()
        self.assertEqual(5000, preview.before["quote"]["total_krw"])
        self.assertEqual(8500, preview.after["quote"]["total_krw"])
        self.assertEqual("Keep this note.", preview.after["manual_note"])
        self.assertFalse((self.root / ".history").exists())
        self.assertEqual(self.original, self.path.read_bytes())
        preview.after["quote"]["total_krw"] = 1
        with patch.object(CATALOG["NOTE-01"], "price_krw", 999):
            result = self.changes.apply(preview.preview_id)
        saved = json.loads(self.path.read_bytes())
        self.assertEqual("applied", result.status)
        self.assertTrue(result.current_matches)
        self.assertEqual(8500, saved["quote"]["total_krw"])
        self.assertEqual("Keep this note.", saved["manual_note"])
        self.assertEqual("edit-001", saved["revision"])

    def test_new_preview_invalidates_older_preview_without_journaling_it(self):
        old = self.preview("first")
        new = self.preview("second", quantity=3)
        with self.assertRaisesRegex(DraftError, "INVALID_PREVIEW"):
            self.changes.apply(old.preview_id)
        self.assertFalse((self.root / ".history").exists())
        self.changes.apply(new.preview_id)
        self.assertEqual(12000, json.loads(self.path.read_bytes())["quote"]["total_krw"])

    def test_external_edit_after_preview_is_preserved_and_can_be_previewed_again(self):
        preview = self.preview()
        document = json.loads(self.original)
        document["manual_note"] = "Changed outside the server."
        edited = json.dumps(document).encode()
        self.path.write_bytes(edited)
        with self.assertRaisesRegex(DraftError, "VERSION_CONFLICT"):
            self.changes.apply(preview.preview_id)
        self.assertEqual(edited, self.path.read_bytes())
        self.assertFalse((self.root / ".history").exists())
        self.apply_edit("fresh")
        self.assertEqual("Changed outside the server.", json.loads(self.path.read_bytes())["manual_note"])

    def test_operation_id_cannot_be_rebound_to_a_different_edit(self):
        self.apply_edit()
        original = self.path.read_bytes()
        with self.assertRaisesRegex(DraftError, "OPERATION_CONFLICT"):
            self.preview(budget=9000)
        another = self.store.preview("draft-002", quote(1))
        self.store.save(another.preview_id)
        with self.assertRaisesRegex(DraftError, "OPERATION_CONFLICT"):
            self.changes.preview_edit("draft-002", "edit-001", quote())
        self.assertEqual(original, self.path.read_bytes())

    def test_restart_retry_uses_history_and_keeps_file_and_timestamp(self):
        first = self.preview()
        self.changes.apply(first.preview_id)
        raw, timestamp = self.path.read_bytes(), self.path.stat().st_mtime_ns
        restarted = DraftChanges(DraftStore(self.root))
        with self.assertRaisesRegex(DraftError, "INVALID_PREVIEW"):
            restarted.apply(first.preview_id)
        self.assertEqual("applied", restarted.get_operation("edit-001").status)
        self.assertEqual("already_applied", restarted.resume("edit-001").status)
        fresh = restarted.preview_edit("draft-001", "edit-001", quote())
        self.assertEqual("applied", fresh.operation_state)
        self.assertEqual("already_applied", restarted.apply(fresh.preview_id).status)
        self.assertEqual(raw, self.path.read_bytes())
        self.assertEqual(timestamp, self.path.stat().st_mtime_ns)
        self.assertEqual(1, len(restarted.history("draft-001")))

    def test_retry_after_later_edit_reports_history_without_replaying_it(self):
        self.apply_edit()
        self.apply_edit("edit-002", quantity=3)
        latest = self.path.read_bytes()
        retry = self.changes.resume("edit-001")
        self.assertEqual("already_applied", retry.status)
        self.assertFalse(retry.current_matches)
        self.assertEqual(8500, retry.total_krw)
        self.assertEqual(latest, self.path.read_bytes())
        with self.assertRaisesRegex(DraftError, "UNDO_CONFLICT"):
            self.changes.preview_undo("edit-001", "undo-old")

    def test_undo_restores_exact_bytes_and_is_repeatable_after_restart(self):
        self.apply_edit()
        edited = self.path.read_bytes()
        undo = self.changes.preview_undo("edit-001", "undo-001")
        self.assertEqual(8500, undo.before["quote"]["total_krw"])
        self.assertEqual(5000, undo.after["quote"]["total_krw"])
        self.assertEqual(edited, self.path.read_bytes())
        result = self.changes.apply(undo.preview_id)
        self.assertEqual("undo", result.action)
        self.assertTrue(result.current_matches)
        self.assertEqual(self.original, self.path.read_bytes())
        timestamp = self.path.stat().st_mtime_ns
        restarted = DraftChanges(DraftStore(self.root))
        self.assertEqual("already_applied", restarted.resume("undo-001").status)
        fresh = restarted.preview_undo("edit-001", "undo-001")
        self.assertEqual("already_applied", restarted.apply(fresh.preview_id).status)
        self.assertEqual(self.original, self.path.read_bytes())
        self.assertEqual(timestamp, self.path.stat().st_mtime_ns)
        self.assertEqual(["edit", "undo"], [r.action for r in restarted.history("draft-001")])

    def test_later_edit_and_undo_still_prevent_undoing_an_older_operation(self):
        self.apply_edit("edit-a")
        first = self.path.read_bytes()
        self.apply_edit("edit-b", quantity=3)
        undo_b = self.changes.preview_undo("edit-b", "undo-b")
        self.changes.apply(undo_b.preview_id)
        self.assertEqual(first, self.path.read_bytes())
        with self.assertRaisesRegex(DraftError, "UNDO_CONFLICT"):
            self.changes.preview_undo("edit-a", "undo-a")
        self.assertEqual(first, self.path.read_bytes())

    def test_external_timestamp_change_prevents_undo_even_with_identical_bytes(self):
        self.apply_edit()
        raw = self.path.read_bytes()
        before = self.path.stat()
        os.utime(self.path, ns=(before.st_atime_ns, before.st_mtime_ns + 1_000_000_000))
        with self.assertRaisesRegex(DraftError, "UNDO_CONFLICT"):
            self.changes.preview_undo("edit-001", "undo-001")
        self.assertEqual(raw, self.path.read_bytes())

    def test_edit_after_undo_preview_is_preserved_at_apply(self):
        self.apply_edit()
        undo = self.changes.preview_undo("edit-001", "undo-001")
        edited = self.path.read_bytes() + b"\n"
        self.path.write_bytes(edited)
        with self.assertRaisesRegex(DraftError, "VERSION_CONFLICT"):
            self.changes.apply(undo.preview_id)
        self.assertEqual(edited, self.path.read_bytes())
        self.assertEqual(1, len(self.changes.history("draft-001")))

    def test_unknown_status_and_resume_do_not_create_history_or_authorize_changes(self):
        self.assertEqual([], self.changes.history("draft-001"))
        for function in (self.changes.get_operation, self.changes.resume):
            with self.assertRaisesRegex(DraftError, "UNKNOWN_OPERATION"):
                function("never-applied")
        preview = self.preview("unapproved")
        with self.assertRaisesRegex(DraftError, "UNKNOWN_OPERATION"):
            self.changes.resume(preview.operation_id)
        self.assertFalse((self.root / ".history").exists())
        self.assertEqual(self.original, self.path.read_bytes())

    def test_prepared_failure_blocks_other_changes_and_resumes_exact_request(self):
        preview = self.preview()
        with patch.object(self.changes, "_replace", side_effect=PermissionError("fixture")):
            with self.assertRaisesRegex(DraftError, "CHANGE_PENDING"):
                self.changes.apply(preview.preview_id)
        self.assertEqual("prepared", self.changes.get_operation("edit-001").status)
        self.assertEqual(self.original, self.path.read_bytes())
        with self.assertRaisesRegex(DraftError, "RECOVERY_REQUIRED"):
            self.preview("edit-other")
        result = self.changes.resume("edit-001")
        self.assertEqual("applied", result.status)
        self.assertEqual(8500, result.total_krw)

    def test_failure_after_publication_recovers_without_rewriting_file(self):
        real_replace = self.changes._replace
        def replace_then_fail(*args):
            real_replace(*args)
            raise OSError("response lost after publication")
        with patch.object(self.changes, "_replace", side_effect=replace_then_fail):
            with self.assertRaisesRegex(DraftError, "CHANGE_PENDING"):
                self.apply_edit()
        raw, timestamp = self.path.read_bytes(), self.path.stat().st_mtime_ns
        self.assertEqual(8500, json.loads(raw)["quote"]["total_krw"])
        self.assertEqual("prepared", self.changes.get_operation("edit-001").status)
        with patch.object(self.changes, "_replace", side_effect=AssertionError("must not rewrite")):
            result = self.changes.resume("edit-001")
        self.assertEqual("applied", result.status)
        self.assertEqual(raw, self.path.read_bytes())
        self.assertEqual(timestamp, self.path.stat().st_mtime_ns)


    def test_recovery_refuses_external_rewrite_of_the_same_after_bytes(self):
        real_replace = self.changes._replace
        def replace_then_fail(*args):
            real_replace(*args)
            raise OSError("after publication, before final journal commit")
        with patch.object(self.changes, "_replace", side_effect=replace_then_fail):
            with self.assertRaisesRegex(DraftError, "CHANGE_PENDING"):
                self.apply_edit()
        written = self.path.read_bytes()
        info = self.path.stat()
        self.path.write_bytes(written + b"\n")
        self.path.write_bytes(written)
        os.utime(self.path, ns=(info.st_atime_ns, info.st_mtime_ns + 1_000_000_000))
        restarted = DraftChanges(DraftStore(self.root))
        with self.assertRaisesRegex(DraftError, "RECOVERY_CONFLICT"):
            restarted.resume("edit-001")
        self.assertEqual(written, self.path.read_bytes())
        self.assertEqual("conflict", restarted.get_operation("edit-001").status)
        with self.assertRaisesRegex(DraftError, "UNDO_UNAVAILABLE"):
            restarted.preview_undo("edit-001", "must-not-undo")

    def test_recovery_preserves_external_content_and_marks_terminal_conflict(self):
        with patch.object(self.changes, "_replace", side_effect=OSError("fixture")):
            with self.assertRaisesRegex(DraftError, "CHANGE_PENDING"):
                self.apply_edit()
        edited = self.original + b"\n"
        self.path.write_bytes(edited)
        with self.assertRaisesRegex(DraftError, "RECOVERY_CONFLICT"):
            self.changes.resume("edit-001")
        self.assertEqual("conflict", self.changes.get_operation("edit-001").status)
        self.assertEqual(edited, self.path.read_bytes())
        with self.assertRaisesRegex(DraftError, "RECOVERY_CONFLICT"):
            self.changes.resume("edit-001")
        self.assertEqual("new", self.preview("different-operation").operation_state)

    def test_external_change_during_staging_is_checked_before_replace(self):
        real_fsync = os.fsync
        edited = self.original + b"\n\n"
        def change_while_flushing(fd):
            self.path.write_bytes(edited)
            real_fsync(fd)
        with patch("learning_lab_mcp.changes.os.fsync", side_effect=change_while_flushing):
            with self.assertRaisesRegex(DraftError, "RECOVERY_CONFLICT"):
                self.apply_edit()
        self.assertEqual(edited, self.path.read_bytes())
        self.assertEqual("conflict", self.changes.get_operation("edit-001").status)
        self.assertEqual([], list((self.root / ".history").glob("pending-*.tmp")))
        self.assertFalse(self.changes.get_operation("edit-001").cleanup_pending)

    def test_journal_failure_is_not_reported_as_success(self):
        preview = self.preview()
        with patch("learning_lab_mcp.changes.sqlite3.connect", side_effect=sqlite3.OperationalError("fixture")):
            with self.assertRaisesRegex(DraftError, "HISTORY_UNAVAILABLE"):
                self.changes.apply(preview.preview_id)
        self.assertEqual(self.original, self.path.read_bytes())

    def test_cleanup_state_is_separate_from_applied_state(self):
        with patch.object(self.changes, "_cleanup", return_value=True):
            result = self.apply_edit()
        self.assertEqual("applied", result.status)
        self.assertTrue(result.cleanup_pending)
        self.assertTrue(self.changes.get_operation("edit-001").cleanup_pending)

    def test_invalid_draft_or_request_cannot_create_a_change(self):
        for operation in ("../outside", "con", "a/b"):
            with self.subTest(operation=operation):
                with self.assertRaisesRegex(DraftError, "INVALID_REQUEST_ID"):
                    self.preview(operation)
        for invalid in (b"{", b"{}", self.original.replace(b"draft-001", b"wrong-id")):
            with self.subTest(invalid=invalid):
                self.path.write_bytes(invalid)
                with self.assertRaisesRegex(DraftError, "INVALID_DRAFT"):
                    self.preview()
                self.assertEqual(invalid, self.path.read_bytes())
        self.assertFalse((self.root / ".history").exists())

    def test_history_reparse_point_is_rejected_without_requiring_symlink_privilege(self):
        history = self.root / ".history"
        history.mkdir()
        real_stat = self.store._checked_stat
        def reject_history(path):
            if path == history:
                raise DraftError("UNSAFE_STORAGE_PATH: fixture reparse point")
            return real_stat(path)
        with patch.object(self.store, "_checked_stat", side_effect=reject_history):
            with self.assertRaisesRegex(DraftError, "UNSAFE_STORAGE_PATH"):
                self.preview()
        self.assertEqual(self.original, self.path.read_bytes())


_CRASH_SCRIPT = r"""
import os, sqlite3, sys
from pathlib import Path
from learning_lab_mcp.changes import DraftChanges
from learning_lab_mcp.drafts import DraftStore
from learning_lab_mcp.server import PurchaseItem, review_purchase
root, checkpoint = Path(sys.argv[1]), sys.argv[2]
changes = DraftChanges(DraftStore(root))
if len(sys.argv) > 3 and sys.argv[3] == "undo":
    preview = changes.preview_undo("setup-edit", "crash-001")
else:
    preview = changes.preview_edit("draft-001", "crash-001", review_purchase([
        PurchaseItem(product_id="NOTE-01", quantity=2),
        PurchaseItem(product_id="PEN-02", quantity=1),
    ], 8000))
real_replace = changes._replace
def crash(row, db):
    if checkpoint == "during":
        staging = changes._staging(row)
        with staging.open("wb") as stream:
            stream.write(row["after_bytes"][:20])
            stream.flush()
            os.fsync(stream.fileno())
    elif checkpoint == "after":
        real_replace(row, db)
    os._exit(71)
if checkpoint in ("commit", "staging-before", "staging-after"):
    real_connect = sqlite3.connect
    class CrashConnection(sqlite3.Connection):
        def commit(self):
            row = self.execute("SELECT state,staged_mtime FROM changes WHERE operation_id='crash-001'").fetchone()
            if row is not None:
                if checkpoint == "commit" and row[0] == "applied":
                    os._exit(71)
                if checkpoint.startswith("staging-") and row[0] == "prepared" and row[1] is not None:
                    if checkpoint == "staging-after":
                        super().commit()
                    os._exit(71)
            return super().commit()
    def connect(*args, **kwargs):
        kwargs["factory"] = CrashConnection
        return real_connect(*args, **kwargs)
    sqlite3.connect = connect
else:
    changes._replace = crash
changes.apply(preview.preview_id)
raise AssertionError("Crash checkpoint was not reached")
"""


class ProcessCrashTests(unittest.TestCase):
    def test_process_death_before_during_after_publication_and_before_commit(self):
        cases = [("edit", point) for point in (
            "before", "during", "staging-before", "staging-after", "after", "commit"
        )] + [("undo", "before"), ("undo", "after")]
        for action, checkpoint in cases:
            with self.subTest(action=action, checkpoint=checkpoint), tempfile.TemporaryDirectory() as directory:
                root = Path(directory) / "drafts"
                store, path = seed(root)
                original = path.read_bytes()
                if action == "undo":
                    setup = DraftChanges(store)
                    setup.apply(setup.preview_edit("draft-001", "setup-edit", quote()).preview_id)
                before_operation = path.read_bytes()
                expected_total = 5000 if action == "undo" else 8500
                completed = subprocess.run(
                    [sys.executable, "-B", "-c", _CRASH_SCRIPT, str(root), checkpoint, action],
                    capture_output=True, text=True, timeout=20,
                )
                self.assertEqual(71, completed.returncode, completed.stderr)
                if checkpoint in ("before", "during", "staging-before", "staging-after"):
                    self.assertEqual(before_operation, path.read_bytes())
                else:
                    self.assertEqual(expected_total, json.loads(path.read_bytes())["quote"]["total_krw"])
                timestamp = path.stat().st_mtime_ns
                restarted = DraftChanges(DraftStore(root))
                result = restarted.resume("crash-001")
                self.assertEqual("applied", result.status)
                self.assertTrue(result.current_matches)
                self.assertEqual(expected_total, json.loads(path.read_bytes())["quote"]["total_krw"])
                if action == "undo":
                    self.assertEqual(original, path.read_bytes())
                self.assertEqual([], list((root / ".history").glob("pending-*.tmp")))
                if checkpoint in ("after", "commit"):
                    self.assertEqual(timestamp, path.stat().st_mtime_ns)
                self.assertEqual("already_applied", restarted.resume("crash-001").status)


class ChangeMcpTests(unittest.IsolatedAsyncioTestCase):
    async def test_inspector_tools_and_default_host_boundary(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "drafts"
            store, path = seed(root)
            async with Client(create_inspector_server(root)) as client:
                names = {t.name: t for t in (await client.list_tools()).tools}
                self.assertEqual(11, len(names))
                self.assertFalse(names["apply_draft_change"].annotations.read_only_hint)
                self.assertTrue(names["apply_draft_change"].annotations.destructive_hint)
                self.assertTrue(names["resume_draft_operation"].annotations.destructive_hint)
                for arguments in (
                    {"draft_id":"draft-001","operation_id":"edit","items":[{"product_id":"NOTE-01","quantity":0}],"budget_krw":8000},
                    {"draft_id":"draft-001","operation_id":"edit","items":[{"product_id":"UNKNOWN","quantity":1}],"budget_krw":8000},
                ):
                    result = await client.call_tool("preview_draft_edit", arguments)
                    self.assertTrue(result.is_error)
                self.assertFalse((root / ".history").exists())
            async with Client(mcp) as client:
                self.assertEqual({"get_product","find_products","review_purchase"},
                                 {t.name for t in (await client.list_tools()).tools})
                for name in ("apply_draft_change","resume_draft_operation","preview_draft_edit","preview_draft_undo"):
                    result = await client.call_tool(name, {"preview_id":"unapproved"})
                    self.assertTrue(result.is_error)

    async def test_stdio_edit_restart_retry_and_undo(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "drafts"
            store, path = seed(root)
            original = path.read_bytes()
            parameters = StdioServerParameters(command=sys.executable, args=[
                "-B", "-c", "import sys; from pathlib import Path; "
                "from learning_lab_mcp.inspector import create_inspector_server; "
                "create_inspector_server(Path(sys.argv[1])).run()", str(root),
            ])
            async with Client(stdio_client(parameters), read_timeout_seconds=15) as client:
                preview = await client.call_tool("preview_draft_edit", {
                    "draft_id":"draft-001", "operation_id":"stdio-edit",
                    "items":[{"product_id":"NOTE-01","quantity":2},{"product_id":"PEN-02","quantity":1}],
                    "budget_krw":8000,
                })
                self.assertFalse(preview.is_error, preview)
                self.assertFalse((root / ".history").exists())
                token = {"preview_id":preview.structured_content["preview_id"]}
                applied = await client.call_tool("apply_draft_change", token)
                self.assertFalse(applied.is_error, applied)
                self.assertEqual("applied", applied.structured_content["status"])
            edited, timestamp = path.read_bytes(), path.stat().st_mtime_ns
            async with Client(stdio_client(parameters), read_timeout_seconds=15) as client:
                status = await client.call_tool("get_draft_operation", {"operation_id":"stdio-edit"})
                self.assertFalse(status.is_error, status)
                self.assertEqual("applied", status.structured_content["status"])
                stale = await client.call_tool("apply_draft_change", token)
                self.assertTrue(stale.is_error)
                resumed = await client.call_tool("resume_draft_operation", {"operation_id":"stdio-edit"})
                self.assertEqual("already_applied", resumed.structured_content["status"])
                self.assertEqual(edited, path.read_bytes())
                self.assertEqual(timestamp, path.stat().st_mtime_ns)
                undo = await client.call_tool("preview_draft_undo", {
                    "target_operation_id":"stdio-edit", "operation_id":"stdio-undo",
                })
                self.assertFalse(undo.is_error, undo)
                restored = await client.call_tool("apply_draft_change", {
                    "preview_id":undo.structured_content["preview_id"],
                })
                self.assertFalse(restored.is_error, restored)
                self.assertEqual(original, path.read_bytes())
                history = await client.call_tool("get_draft_history", {"draft_id":"draft-001"})
                self.assertFalse(history.is_error, history)
                self.assertEqual(2, len(history.structured_content["result"]))


if __name__ == "__main__":
    unittest.main()
