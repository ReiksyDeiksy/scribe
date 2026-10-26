"""Retention for app-created dictations; untracked and exported files are preserved."""

from __future__ import annotations

import json
import logging
import math
import os
from pathlib import Path
import re
import stat
import tempfile
import threading
import time
from uuid import uuid4

from .config import DICTATION_RETENTION_DAYS

logger = logging.getLogger(__name__)
_INDEX_NAME = ".dictation-history.json"
_FILENAME = re.compile(r"dictation_[0-9a-f]{32}\.txt")
_LOCK = threading.RLock()


def _check_regular_path(path: Path, *, directory: bool = False) -> os.stat_result:
    info = path.lstat()
    is_link = stat.S_ISLNK(info.st_mode) or (
        getattr(info, "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0)
    )
    valid_type = stat.S_ISDIR(info.st_mode) if directory else stat.S_ISREG(info.st_mode)
    if is_link or not valid_type:
        raise OSError(f"Dictation history path is not a regular {'directory' if directory else 'file'}: {path}")
    return info


def _signature(info: os.stat_result) -> list[int]:
    return [info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns]


def _read_index(directory: Path) -> dict:
    _check_regular_path(directory, directory=True)
    path = directory / _INDEX_NAME
    try:
        _check_regular_path(path)
    except FileNotFoundError:
        return {}
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, UnicodeError) as exc:
        raise OSError(f"Invalid dictation history index: {path}") from exc
    if not isinstance(payload, dict) or payload.get("version") != 1 or not isinstance(payload.get("files"), dict):
        raise OSError(f"Invalid dictation history index: {path}")
    for name, entry in payload["files"].items():
        if not isinstance(name, str) or not _FILENAME.fullmatch(name) or not isinstance(entry, dict):
            raise OSError(f"Invalid dictation history entry: {path}")
        created = entry.get("created_at")
        signature = entry.get("signature")
        try:
            valid_created = type(created) in (int, float) and created >= 0 and math.isfinite(created)
        except OverflowError:
            valid_created = False
        if (not valid_created
                or not isinstance(signature, list) or len(signature) != 4
                or any(type(value) is not int for value in signature)):
            raise OSError(f"Invalid dictation history entry: {path}")
    return payload["files"]


def _write_index(directory: Path, files: dict) -> None:
    _check_regular_path(directory, directory=True)
    temporary_path = None
    try:
        # Replace atomically so an interrupted update cannot authorize deletion from a partial index.
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=directory,
                                         prefix=".dictation-history-", suffix=".tmp", delete=False) as stream:
            temporary_path = Path(stream.name)
            json.dump({"version": 1, "files": files}, stream, ensure_ascii=False)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary_path, directory / _INDEX_NAME)
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)


def save_dictation_transcript(output_directory: Path, text: str) -> Path:
    directory = output_directory / "dictations"
    directory.mkdir(parents=True, exist_ok=True)
    _check_regular_path(directory, directory=True)
    path = directory / f"dictation_{uuid4().hex}.txt"
    with _LOCK:
        with path.open("x", encoding="utf-8") as stream:
            stream.write(text)
        try:
            files = _read_index(directory)
            files[path.name] = {"created_at": time.time(), "signature": _signature(_check_regular_path(path))}
            _write_index(directory, files)
        except OSError:
            # A recovery copy must remain available even if tracking fails; untracked files never expire.
            logger.exception("Dictation saved without automatic expiration: %s", path)
    return path


def preserve_dictation_transcript(path: Path) -> None:
    """Exclude an explicit export before overwriting any tracked autosave."""
    path = path.resolve()
    if not _FILENAME.fullmatch(path.name) or not path.parent.exists():
        return
    with _LOCK:
        files = _read_index(path.parent)
        if path.name in files:
            del files[path.name]
            _write_index(path.parent, files)


def cleanup_dictation_history(
    output_directory: Path, retention: str, *, now: float | None = None,
) -> list[Path]:
    days = DICTATION_RETENTION_DAYS.get(retention)
    if days is None:
        return []
    directory = output_directory / "dictations"
    if not directory.exists():
        return []
    now = time.time() if now is None else now
    deleted = []
    with _LOCK:
        try:
            files = _read_index(directory)
            changed = False
            for name, entry in list(files.items()):
                path = directory / name
                try:
                    info = _check_regular_path(path)
                except FileNotFoundError:
                    del files[name]
                    changed = True
                    continue
                except OSError:
                    logger.warning("Dictation history file left untouched: %s", path, exc_info=True)
                    continue
                if _signature(info) != entry["signature"]:
                    # Treat a modified or replaced file as user-owned, regardless of its age.
                    del files[name]
                    changed = True
                    continue
                if now - entry["created_at"] < days * 86400:
                    continue
                try:
                    path.unlink()
                except OSError:
                    logger.warning("Expired dictation could not be deleted: %s", path, exc_info=True)
                    continue
                deleted.append(path)
                del files[name]
                changed = True
                logger.info("Expired dictation deleted: %s", path)
            if changed:
                _write_index(directory, files)
        except OSError:
            logger.exception("Dictation history cleanup could not finish: %s", directory)
    return deleted
