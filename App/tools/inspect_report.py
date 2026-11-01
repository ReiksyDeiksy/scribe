"""Validate and summarize a scribe report on Windows without extracting or executing it.

Usage: python tools/inspect_report.py path/to/scribe-report-....zip
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import zipfile

MAX_BYTES = 256 * 1024 * 1024
MAX_JSON = 4 * 1024 * 1024
TEXT_FILES = {"report.json", "diagnostics.json", "README.txt", "transcript.json", "transcript.txt"}


def inspect_report(path):
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        if len(names) != len(set(names)) or len(entries) > 2000:
            raise ValueError("Duplicate names or too many entries")
        if sum(entry.file_size for entry in entries) > MAX_BYTES:
            raise ValueError("Report exceeds the uncompressed size limit")
        for entry in entries:
            if entry.filename not in TEXT_FILES and not re.fullmatch(
                r"audio/section-[0-9]{5}\.[a-z0-9]{1,8}", entry.filename
            ):
                raise ValueError("Unexpected archive entry")
            if entry.flag_bits & 1:
                raise ValueError("Encrypted archive entries are not supported")

        def read_json(name):
            if archive.getinfo(name).file_size > MAX_JSON:
                raise ValueError("JSON entry is too large")
            return json.loads(archive.read(name))

        report = read_json("report.json")
        if report.get("schema_version") != 1:
            raise ValueError("Unsupported report schema")
        events = read_json("diagnostics.json")
        if not isinstance(events, list) or len(events) > 2048:
            raise ValueError("Invalid diagnostics")
        attachments = report["audio"]
        if not isinstance(attachments, list):
            raise ValueError("Invalid audio manifest")
        attached = set()
        for item in attachments:
            name = item["file"]
            if name in attached or not name.startswith("audio/"):
                raise ValueError("Invalid or duplicate audio attachment")
            attached.add(name)
            entry = archive.getinfo(name)
            if entry.file_size != item["bytes"]:
                raise ValueError("Audio size mismatch")
            with archive.open(name) as stream:
                digest = hashlib.file_digest(stream, "sha256").hexdigest()
            if digest != item["sha256"]:
                raise ValueError("Audio checksum mismatch")
        if attached != {name for name in names if name.startswith("audio/")}:
            raise ValueError("Audio manifest does not match the archive")
        if not report.get("audio_requested") and attached:
            raise ValueError("Unexpected audio in diagnostics-only report")
        text = read_json("transcript.json") if report["transcript_included"] else None
        if not report["transcript_included"] and any(name.startswith("transcript.") for name in names):
            raise ValueError("Unexpected transcript attachment")
        windows = [event["data"] for event in events if event.get("kind") == "window_processed"]
        audio_ms = sum(window.get("audio_ms", 0) for window in windows)
        recognition_ms = sum(window.get("recognition_elapsed_ms", 0) for window in windows)
        return {
            "report_id": report["report_id"],
            "problem": report["problem"],
            "note": report["note"],
            "expected_text": report["expected_text"],
            "app_at_export": report["app_at_export"],
            "lecture": report["lecture"],
            "selection": report["selection"],
            "audio_files_verified": len(attachments),
            "missing_audio_sections": len(report["missing_audio"]),
            "transcript_passages": len(text["passages"]) if text else 0,
            "diagnostic_events": len(events),
            "measured_windows": len(windows),
            "recognition_seconds": recognition_ms / 1000,
            "recognition_seconds_per_processed_audio_second": recognition_ms / audio_ms if audio_ms else None,
            "timing_note": "Window totals include overlap and any retries; they are not total lecture processing time.",
        }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    args = parser.parse_args()
    try:
        summary = inspect_report(args.report)
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile, RuntimeError) as error:
        print(f"Invalid report: {type(error).__name__}", file=sys.stderr)
        return 1
    # Escaping control characters keeps notes from acting as terminal escape sequences.
    print(json.dumps(summary, indent=2, ensure_ascii=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
