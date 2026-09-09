"""Preview snapshots and exclusive creation of local purchase draft files."""
from __future__ import annotations

from dataclasses import dataclass
import json
import os
from pathlib import Path
import re
import stat
import tempfile
from threading import RLock
from typing import Literal
from uuid import uuid4

from pydantic import BaseModel

from .server import PurchaseReview

DEFAULT_DRAFT_ROOT = Path(__file__).resolve().parents[3] / ".local" / "drafts"
_REQUEST_ID = re.compile(r"[a-z0-9][a-z0-9_-]{0,63}", re.ASCII)
_RESERVED = {"con", "prn", "aux", "nul"} | {
    f"{prefix}{number}" for prefix in ("com", "lpt") for number in range(1, 10)
}


class DraftError(ValueError):
    """A draft request cannot be completed without changing its conditions."""


class DraftPreview(BaseModel):
    preview_id: str
    request_id: str
    path: str
    pricing_policy: Literal["preview_snapshot"] = "preview_snapshot"
    estimate_complete: Literal[True] = True
    quote: PurchaseReview


class DraftSaved(BaseModel):
    status: Literal["saved", "already_saved"]
    request_id: str
    path: str
    total_krw: int
    cleanup_pending: bool = False


@dataclass(frozen=True)
class _Snapshot:
    request_id: str
    payload: bytes
    total_krw: int


class DraftStore:
    """One writer process; serialize requests and publish complete files only.

    Preview IDs identify snapshots, not human approval. The Inspector operator
    supplies human confirmation by directly invoking the save tool.
    """

    def __init__(self, root: Path = DEFAULT_DRAFT_ROOT) -> None:
        self.root = root.absolute()
        self._lock = RLock()
        self._previews: dict[str, _Snapshot] = {}
        self._latest: dict[str, str] = {}

    @staticmethod
    def _checked_stat(path: Path) -> os.stat_result | None:
        try:
            info = path.lstat()
        except FileNotFoundError:
            return None
        # Reparse points include Windows junctions, also on Python 3.11.
        if stat.S_ISLNK(info.st_mode) or (
            getattr(info, "st_file_attributes", 0)
            & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
        ):
            raise DraftError("UNSAFE_STORAGE_PATH: links and junctions are not allowed.")
        return info

    def _check_root(self) -> None:
        for directory in reversed((self.root, *self.root.parents)):
            info = self._checked_stat(directory)
            if info is not None and not stat.S_ISDIR(info.st_mode):
                raise DraftError("UNSAFE_STORAGE_PATH: the storage root must be a directory.")

    def _destination(self, request_id: str) -> Path:
        if not _REQUEST_ID.fullmatch(request_id) or request_id in _RESERVED:
            raise DraftError(
                "INVALID_REQUEST_ID: use 1-64 lowercase letters, digits, '-' or '_'; "
                "start with a letter or digit and avoid reserved device names."
            )
        self._check_root()
        return self.root / f"{request_id}.json"

    def preview(self, request_id: str, quote: PurchaseReview) -> DraftPreview:
        with self._lock:
            destination = self._destination(request_id)
            document = {
                "schema_version": 1,
                "request_id": request_id,
                "pricing_policy": "preview_snapshot",
                "estimate_complete": True,
                "quote": quote.model_dump(mode="json"),
            }
            payload = (json.dumps(
                document, ensure_ascii=False, sort_keys=True, indent=2,
            ) + "\n").encode("utf-8")
            token = uuid4().hex
            old_token = self._latest.get(request_id)
            if old_token is not None:
                self._previews.pop(old_token, None)
            self._latest[request_id] = token
            self._previews[token] = _Snapshot(request_id, payload, quote.total_krw)
            return DraftPreview(
                preview_id=token, request_id=request_id, path=str(destination),
                quote=quote.model_copy(deep=True),
            )

    def _existing(
        self, destination: Path, snapshot: _Snapshot,
    ) -> DraftSaved | None:
        info = self._checked_stat(destination)
        if info is None:
            return None
        if (
            not stat.S_ISREG(info.st_mode)
            or info.st_size != len(snapshot.payload)
            or destination.read_bytes() != snapshot.payload
        ):
            raise DraftError(
                "DRAFT_CONFLICT: the destination already contains different or edited content."
            )
        return DraftSaved(
            status="already_saved", request_id=snapshot.request_id,
            path=str(destination), total_krw=snapshot.total_krw,
        )

    def _publish(self, destination: Path, payload: bytes) -> bool:
        """Write+flush a temporary file, then link it to an unused final name.

        os.link is exclusive: unlike os.replace, it cannot overwrite a draft.
        Filesystems without hard-link support fail rather than using an unsafe
        fallback. Pending files are never accepted as saved drafts.
        """
        temporary: Path | None = None
        cleanup_pending = False
        try:
            fd, name = tempfile.mkstemp(prefix=".pending-", suffix=".tmp", dir=self.root)
            temporary = Path(name)
            with os.fdopen(fd, "wb") as stream:
                stream.write(payload)
                stream.flush()
                os.fsync(stream.fileno())
            os.link(temporary, destination)
        finally:
            if temporary is not None:
                try:
                    temporary.unlink()
                except OSError:
                    cleanup_pending = True
        return cleanup_pending

    def save(self, preview_id: str) -> DraftSaved:
        with self._lock:
            snapshot = self._previews.get(preview_id)
            if snapshot is None:
                raise DraftError(
                    "INVALID_PREVIEW: preview again after replacement or server restart."
                )
            destination = self._destination(snapshot.request_id)
            try:
                existing = self._existing(destination, snapshot)
                if existing is not None:
                    return existing
                self.root.mkdir(parents=True, exist_ok=True)
                self._check_root()
                try:
                    cleanup_pending = self._publish(destination, snapshot.payload)
                except FileExistsError:
                    # Another file appeared after the initial check. Never replace it.
                    existing = self._existing(destination, snapshot)
                    if existing is None:
                        raise DraftError("SAVE_FAILED: destination changed; inspect and retry.")
                    return existing
            except OSError as error:
                raise DraftError(
                    "SAVE_FAILED: draft publication failed; inspect the destination "
                    "before retrying. Existing drafts were not replaced."
                ) from error
            return DraftSaved(
                status="saved", request_id=snapshot.request_id,
                path=str(destination), total_krw=snapshot.total_krw,
                cleanup_pending=cleanup_pending,
            )
