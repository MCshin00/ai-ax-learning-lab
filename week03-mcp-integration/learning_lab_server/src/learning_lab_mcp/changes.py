"""Version-checked draft edits with a durable journal and explicit recovery."""
from __future__ import annotations

from contextlib import contextmanager
from dataclasses import dataclass
from hashlib import sha256
import json
import os
from pathlib import Path
import re
import sqlite3
import stat
from typing import Any, Literal
from uuid import uuid4

from pydantic import BaseModel, ValidationError

from .drafts import DraftError, DraftStore
from .server import PurchaseReview


class ChangePreview(BaseModel):
    preview_id: str
    operation_id: str
    draft_id: str
    action: Literal["edit", "undo"]
    target_operation_id: str | None = None
    path: str
    base_version: str
    before: dict[str, Any]
    after: dict[str, Any]
    operation_state: Literal["new", "prepared", "applied", "conflict"]


class ChangeResult(BaseModel):
    operation_id: str
    draft_id: str
    action: Literal["edit", "undo"]
    target_operation_id: str | None = None
    status: Literal["prepared", "applied", "already_applied", "conflict"]
    path: str
    total_krw: int
    current_matches: bool
    cleanup_pending: bool


@dataclass(frozen=True)
class FileVersion:
    payload: bytes
    mtime_ns: str
    inode: str


@dataclass(frozen=True)
class Change:
    operation_id: str
    draft_id: str
    action: str
    target_operation_id: str | None
    before: FileVersion
    after: bytes
    base_head: int


_SCHEMA = """
CREATE TABLE IF NOT EXISTS changes (
    sequence INTEGER PRIMARY KEY AUTOINCREMENT,
    operation_id TEXT NOT NULL UNIQUE,
    draft_id TEXT NOT NULL,
    action TEXT NOT NULL CHECK(action IN ('edit', 'undo')),
    target_operation_id TEXT,
    state TEXT NOT NULL CHECK(state IN ('prepared', 'applied', 'conflict')),
    before_bytes BLOB NOT NULL,
    before_mtime TEXT NOT NULL,
    before_inode TEXT NOT NULL,
    after_bytes BLOB NOT NULL,
    staged_mtime TEXT,
    staged_inode TEXT,
    after_mtime TEXT,
    after_inode TEXT,
    base_head INTEGER NOT NULL,
    staging_name TEXT NOT NULL,
    cleanup_pending INTEGER NOT NULL DEFAULT 0
);
"""
_STAGING_NAME = re.compile(r"pending-[a-f0-9]{32}\.tmp")


def _document(raw: bytes, draft_id: str) -> dict[str, Any]:
    try:
        document = json.loads(raw)
        if (
            not isinstance(document, dict)
            or document.get("request_id") != draft_id
            or document.get("schema_version") != 1
            or document.get("estimate_complete") is not True
            or document.get("pricing_policy") != "preview_snapshot"
        ):
            raise ValueError("Unsupported draft")
        PurchaseReview.model_validate(document["quote"])
        return document
    except (ValueError, KeyError, TypeError, ValidationError) as error:
        raise DraftError("INVALID_DRAFT: expected a complete purchase draft with the matching ID.") from error


def _encode(document: dict[str, Any]) -> bytes:
    return (json.dumps(document, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


class DraftChanges:
    """Share the basic store's lock; SQLite serializes journal mutations.

    The local exercise uses one writer server. External editor saves must finish
    before an apply/resume call; editors do not participate in SQLite locks.
    No files, database, or directories are created by previews or status reads.
    """

    def __init__(self, store: DraftStore):
        self.store = store
        self._previews: dict[str, Change] = {}
        self._latest: dict[str, str] = {}

    def _read(self, draft_id: str) -> FileVersion:
        path = self.store._destination(draft_id)
        first = self.store._checked_stat(path)
        if first is None:
            raise DraftError("DRAFT_NOT_FOUND: create a draft before editing it.")
        if not stat.S_ISREG(first.st_mode):
            raise DraftError("INVALID_DRAFT: the draft must be a regular file.")
        payload = path.read_bytes()
        last = self.store._checked_stat(path)
        if last is None or (first.st_ino, first.st_mtime_ns, first.st_size) != (
            last.st_ino, last.st_mtime_ns, last.st_size
        ):
            raise DraftError("VERSION_CONFLICT: the file changed while it was being read.")
        return FileVersion(payload, str(last.st_mtime_ns), str(last.st_ino))

    def _metadata(self, create: bool = False) -> Path | None:
        self.store._check_root()
        folder = self.store.root / ".history"
        info = self.store._checked_stat(folder)
        if info is None:
            if not create:
                return None
            folder.mkdir()
            info = self.store._checked_stat(folder)
        if info is None or not stat.S_ISDIR(info.st_mode):
            raise DraftError("UNSAFE_STORAGE_PATH: history must be a directory.")
        # SQLite's sidecars also stay inside the checked history directory.
        for suffix in ("", "-journal", "-wal", "-shm"):
            candidate = folder / ("changes.sqlite3" + suffix)
            info = self.store._checked_stat(candidate)
            if info is not None and not stat.S_ISREG(info.st_mode):
                raise DraftError("UNSAFE_STORAGE_PATH: history files must be regular files.")
        return folder

    @contextmanager
    def _journal(self, write: bool = False):
        folder = self._metadata(create=write)
        path = folder / "changes.sqlite3" if folder else None
        if path is None or (not write and not path.exists()):
            yield None
            return
        connection = None
        try:
            if write:
                connection = sqlite3.connect(path, isolation_level=None, timeout=5)
                connection.execute("PRAGMA synchronous=FULL")
                connection.executescript(_SCHEMA)
                columns = {row[1] for row in connection.execute("PRAGMA table_info(changes)")}
                for column in ("staged_mtime", "staged_inode"):
                    if column not in columns:
                        connection.execute(f"ALTER TABLE changes ADD COLUMN {column} TEXT")
            else:
                connection = sqlite3.connect(path.as_uri() + "?mode=ro", uri=True, isolation_level=None)
            connection.row_factory = sqlite3.Row
            yield connection
        except sqlite3.Error as error:
            raise DraftError("HISTORY_UNAVAILABLE: inspect storage or resume a known operation requiring recovery.") from error
        finally:
            if connection is not None:
                connection.close()

    @staticmethod
    def _row(db, operation_id: str):
        if db is None:
            return None
        return db.execute("SELECT * FROM changes WHERE operation_id=?", (operation_id,)).fetchone()

    @staticmethod
    def _head(db, draft_id: str) -> int:
        if db is None:
            return 0
        return db.execute(
            "SELECT COALESCE(MAX(sequence),0) FROM changes WHERE draft_id=? AND state='applied'",
            (draft_id,),
        ).fetchone()[0]

    @staticmethod
    def _pending(db, draft_id: str, operation_id: str):
        if db is not None and db.execute(
            "SELECT 1 FROM changes WHERE draft_id=? AND state='prepared' AND operation_id<>?",
            (draft_id, operation_id),
        ).fetchone():
            raise DraftError("RECOVERY_REQUIRED: inspect and resume the pending operation first.")

    @staticmethod
    def _change(row) -> Change:
        return Change(
            row["operation_id"], row["draft_id"], row["action"], row["target_operation_id"],
            FileVersion(row["before_bytes"], row["before_mtime"], row["before_inode"]),
            row["after_bytes"], row["base_head"],
        )

    @staticmethod
    def _same_version(first: FileVersion, second: FileVersion) -> bool:
        return first == second

    def _remember(self, change: Change, operation_state: str) -> ChangePreview:
        token = uuid4().hex
        old = self._latest.get(change.draft_id)
        if old is not None:
            self._previews.pop(old, None)
        self._latest[change.draft_id] = token
        self._previews[token] = change
        return ChangePreview(
            preview_id=token, operation_id=change.operation_id, draft_id=change.draft_id,
            action=change.action, target_operation_id=change.target_operation_id,
            path=str(self.store._destination(change.draft_id)),
            base_version=sha256(change.before.payload).hexdigest(),
            before=_document(change.before.payload, change.draft_id),
            after=_document(change.after, change.draft_id), operation_state=operation_state,
        )

    def preview_edit(self, draft_id: str, operation_id: str, quote: PurchaseReview) -> ChangePreview:
        with self.store._lock:
            self.store._destination(operation_id)
            current = self._read(draft_id)
            before = _document(current.payload, draft_id)
            with self._journal() as db:
                row = self._row(db, operation_id)
                if row is not None:
                    if (
                        row["action"] != "edit" or row["draft_id"] != draft_id
                        or _document(row["after_bytes"], draft_id)["quote"] != quote.model_dump(mode="json")
                    ):
                        raise DraftError("OPERATION_CONFLICT: this operation ID already identifies different content.")
                    return self._remember(self._change(row), row["state"])
                self._pending(db, draft_id, operation_id)
                head = self._head(db, draft_id)
            after = dict(before)
            after["quote"] = quote.model_dump(mode="json")
            after["revision"] = operation_id
            return self._remember(Change(operation_id, draft_id, "edit", None, current, _encode(after), head), "new")

    def preview_undo(self, target_operation_id: str, operation_id: str) -> ChangePreview:
        with self.store._lock:
            self.store._destination(operation_id)
            self.store._destination(target_operation_id)
            with self._journal() as db:
                target = self._row(db, target_operation_id)
                if target is None or target["state"] != "applied" or target["action"] != "edit":
                    raise DraftError("UNDO_UNAVAILABLE: only an applied edit can be undone.")
                previous = self._row(db, operation_id)
                if previous is not None:
                    if previous["action"] != "undo" or previous["target_operation_id"] != target_operation_id:
                        raise DraftError("OPERATION_CONFLICT: the cancellation ID is already in use.")
                    return self._remember(self._change(previous), previous["state"])
                self._pending(db, target["draft_id"], operation_id)
                current = self._read(target["draft_id"])
                if not self._is_current(db, target, current):
                    raise DraftError("UNDO_CONFLICT: a later edit or external file change must be preserved.")
                return self._remember(Change(
                    operation_id, target["draft_id"], "undo", target_operation_id, current,
                    target["before_bytes"], target["sequence"],
                ), "new")

    def _is_current(self, db, row, current: FileVersion | None = None) -> bool:
        try:
            current = current or self._read(row["draft_id"])
        except (DraftError, OSError):
            return False
        return (
            row["state"] == "applied" and self._head(db, row["draft_id"]) == row["sequence"]
            and current.payload == row["after_bytes"]
            and current.mtime_ns == row["after_mtime"] and current.inode == row["after_inode"]
        )

    def _result(self, db, row, repeated: bool = False) -> ChangeResult:
        return ChangeResult(
            operation_id=row["operation_id"], draft_id=row["draft_id"], action=row["action"],
            target_operation_id=row["target_operation_id"],
            status="already_applied" if repeated and row["state"] == "applied" else row["state"],
            path=str(self.store._destination(row["draft_id"])),
            total_krw=_document(row["after_bytes"], row["draft_id"])["quote"]["total_krw"],
            current_matches=self._is_current(db, row),
            cleanup_pending=bool(row["cleanup_pending"]),
        )

    def get_operation(self, operation_id: str) -> ChangeResult:
        with self.store._lock:
            self.store._destination(operation_id)
            with self._journal() as db:
                row = self._row(db, operation_id)
                if row is None:
                    raise DraftError("UNKNOWN_OPERATION: no save request is recorded for this ID.")
                return self._result(db, row)

    def history(self, draft_id: str) -> list[ChangeResult]:
        with self.store._lock:
            self.store._destination(draft_id)
            with self._journal() as db:
                if db is None:
                    return []
                rows = db.execute("SELECT * FROM changes WHERE draft_id=? ORDER BY sequence", (draft_id,)).fetchall()
                return [self._result(db, row) for row in rows]

    def apply(self, preview_id: str) -> ChangeResult:
        with self.store._lock:
            change = self._previews.get(preview_id)
            if change is None:
                raise DraftError("INVALID_PREVIEW: preview again after replacement or restart.")
            # Early comparison keeps stale/unapproved previews out of the journal.
            with self._journal() as db:
                existing = self._row(db, change.operation_id)
            if existing is None:
                if not self._same_version(self._read(change.draft_id), change.before):
                    raise DraftError("VERSION_CONFLICT: the draft changed after preview.")
            try:
                with self._journal(write=True) as db:
                    db.execute("BEGIN IMMEDIATE")
                    existing = self._row(db, change.operation_id)
                    if existing is not None:
                        if self._change(existing) != change:
                            raise DraftError("OPERATION_CONFLICT: this ID already has a different request.")
                        db.commit()
                    else:
                        self._pending(db, change.draft_id, change.operation_id)
                        if self._head(db, change.draft_id) != change.base_head or not self._same_version(
                            self._read(change.draft_id), change.before
                        ):
                            raise DraftError("VERSION_CONFLICT: the draft changed after preview.")
                        db.execute(
                            """INSERT INTO changes
                            (operation_id,draft_id,action,target_operation_id,state,
                             before_bytes,before_mtime,before_inode,after_bytes,base_head,staging_name)
                            VALUES (?,?,?,?,'prepared',?,?,?,?,?,?)""",
                            (change.operation_id, change.draft_id, change.action, change.target_operation_id,
                             change.before.payload, change.before.mtime_ns, change.before.inode,
                             change.after, change.base_head, "pending-" + uuid4().hex + ".tmp"),
                        )
                        # Durable intent exists before the JSON file can be replaced.
                        db.commit()
                return self.resume(change.operation_id)
            except OSError as error:
                raise DraftError("CHANGE_PENDING: inspect this operation ID before retrying.") from error

    def _staging(self, row) -> Path:
        folder = self._metadata()
        name = row["staging_name"]
        if folder is None or not _STAGING_NAME.fullmatch(name):
            raise DraftError("INVALID_HISTORY: staging location is invalid.")
        path = folder / name
        info = self.store._checked_stat(path)
        if info is not None and not stat.S_ISREG(info.st_mode):
            raise DraftError("UNSAFE_STORAGE_PATH: staging must be a regular file.")
        return path

    def _cleanup(self, row) -> bool:
        try:
            self._staging(row).unlink(missing_ok=True)
            return False
        except (OSError, DraftError):
            return True

    def _replace(self, row, db):
        path = self.store._destination(row["draft_id"])
        staging = self._staging(row)
        # Only the journal's own random staging name may be rebuilt after a crash.
        with staging.open("wb") as stream:
            stream.write(row["after_bytes"])
            stream.flush()
            os.fsync(stream.fileno())
        staged = self.store._checked_stat(staging)
        if staged is None or staging.read_bytes() != row["after_bytes"]:
            raise DraftError("VERSION_CONFLICT: staging changed before publication.")
        # Persist the identity that rename will carry to the destination. Without
        # this commit, recovery could mistake an external same-content save for
        # our own publication and subsequently allow an unsafe undo.
        db.execute(
            "UPDATE changes SET staged_mtime=?,staged_inode=? WHERE operation_id=?",
            (str(staged.st_mtime_ns), str(staged.st_ino), row["operation_id"]),
        )
        db.commit()
        db.execute("BEGIN IMMEDIATE")
        latest = self._row(db, row["operation_id"])
        if latest["state"] != "prepared" or self._head(db, row["draft_id"]) != row["base_head"]:
            raise DraftError("VERSION_CONFLICT: the operation changed while staging.")
        # Compare again after slow I/O, immediately before the atomic replacement.
        current = self._read(row["draft_id"])
        if current != self._change(row).before:
            raise DraftError("VERSION_CONFLICT: the draft changed before publication.")
        os.replace(staging, path)

    def resume(self, operation_id: str) -> ChangeResult:
        """Resume only a durable request initiated by an Inspector apply call."""
        with self.store._lock:
            self.store._destination(operation_id)
            # A writable connection lets SQLite recover its own hot journal.
            # Checking existence first avoids creating history for an unknown ID.
            folder = self._metadata()
            if folder is None or not (folder / "changes.sqlite3").exists():
                raise DraftError("UNKNOWN_OPERATION: no save request is recorded.")
            try:
                with self._journal(write=True) as db:
                    db.execute("BEGIN IMMEDIATE")
                    row = self._row(db, operation_id)
                    if row is None:
                        raise DraftError("UNKNOWN_OPERATION: no save request is recorded.")
                    if row["state"] == "applied":
                        db.commit()
                        return self._result(db, row, repeated=True)
                    if row["state"] == "conflict":
                        raise DraftError("RECOVERY_CONFLICT: preserve the file and use a new operation ID.")
                    try:
                        if self._head(db, row["draft_id"]) != row["base_head"]:
                            raise DraftError("VERSION_CONFLICT: a later operation already exists.")
                        current = self._read(row["draft_id"])
                        if current.payload != row["after_bytes"]:
                            if current != self._change(row).before:
                                raise DraftError("VERSION_CONFLICT: neither the before nor after version matches.")
                            self._replace(row, db)
                        # Also covers a process dying after replace and before this commit.
                        written = self._read(row["draft_id"])
                        published = self._row(db, operation_id)
                        if (
                            written.payload != row["after_bytes"]
                            or written.mtime_ns != published["staged_mtime"]
                            or written.inode != published["staged_inode"]
                        ):
                            raise DraftError("VERSION_CONFLICT: the published file was changed externally.")
                    except DraftError:
                        cleanup_pending = self._cleanup(row)
                        db.execute(
                            "UPDATE changes SET state='conflict',cleanup_pending=? WHERE operation_id=? AND state='prepared'",
                            (int(cleanup_pending), operation_id),
                        )
                        db.commit()
                        raise DraftError("RECOVERY_CONFLICT: the current file was preserved; inspect before a new edit.") from None
                    cleanup_pending = self._cleanup(row)
                    db.execute(
                        "UPDATE changes SET state='applied',after_mtime=?,after_inode=?,cleanup_pending=? WHERE operation_id=?",
                        (written.mtime_ns, written.inode, int(cleanup_pending), operation_id),
                    )
                    db.commit()
                    return self._result(db, self._row(db, operation_id))
            except OSError as error:
                raise DraftError("CHANGE_PENDING: inspect and resume this operation ID; do not assume failure or success.") from error
