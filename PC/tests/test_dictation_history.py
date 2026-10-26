import json
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import shutil
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import MagicMock, patch
from uuid import uuid4

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))
from recorder_app.config import AppSettings, SettingsStore
from recorder_app import dictation_history as history
from recorder_app.transcription import FastTranscriptionService, TranscriptionRequest

DAY = 86400
CREATED = 1700000000.0


class DictationHistoryTests(unittest.TestCase):
    def setUp(self):
        self.root = Path(__file__).resolve().parents[1] / "output" / f"dictation-test-{uuid4().hex}"
        self.root.mkdir(parents=True)
        self.addCleanup(shutil.rmtree, self.root)

    def save(self, text="A quick message", created=CREATED):
        with patch.object(history.time, "time", return_value=created):
            return history.save_dictation_transcript(self.root, text)

    def test_default_and_existing_settings_use_two_days_and_roundtrip_all_options(self):
        store = SettingsStore(self.root / "settings.json")
        defaults = AppSettings(output_directory=str(self.root / "data"))
        with patch.object(AppSettings, "with_defaults", return_value=defaults):
            self.assertEqual(store.load().dictation_retention, "2_days")
            store.path.write_text(json.dumps({"language": "ro"}), encoding="utf-8")
            self.assertEqual(store.load().dictation_retention, "2_days")
            settings = store.load()
            for retention in ("2_days", "7_days", "forever"):
                settings.dictation_retention = retention
                store.save(settings)
                loaded = store.load()
                self.assertEqual(loaded.dictation_retention, retention)
                self.assertEqual(loaded.language, "ro")
            for invalid in ("invalid", 2, False, None, {}):
                store.path.write_text(json.dumps({"dictation_retention": invalid}), encoding="utf-8")
                self.assertEqual(store.load().dictation_retention, "2_days")

    def test_saved_dictation_expires_at_exactly_48_hours_across_reloads(self):
        path = self.save("Salut! Grüße 日本語")
        self.assertEqual(path.parent, self.root / "dictations")
        self.assertEqual(path.read_text(encoding="utf-8"), "Salut! Grüße 日本語")
        self.assertEqual(history.cleanup_dictation_history(self.root, "2_days", now=CREATED + 2 * DAY - .001), [])
        self.assertTrue(path.exists())
        self.assertEqual(history.cleanup_dictation_history(self.root, "2_days", now=CREATED + 2 * DAY), [path])
        self.assertFalse(path.exists())
        self.assertEqual(history.cleanup_dictation_history(self.root, "2_days", now=CREATED + 3 * DAY), [])

    def test_seven_days_forever_invalid_option_and_future_timestamp(self):
        path = self.save()
        for retention, now in (("7_days", CREATED + 7 * DAY - 1),
                               ("forever", CREATED + 100 * DAY),
                               ("invalid", CREATED + 100 * DAY), ("2_days", CREATED - DAY)):
            self.assertEqual(history.cleanup_dictation_history(self.root, retention, now=now), [])
            self.assertTrue(path.exists())
        self.assertEqual(history.cleanup_dictation_history(self.root, "7_days", now=CREATED + 7 * DAY), [path])

    def test_regular_legacy_exported_untracked_and_nested_files_are_preserved(self):
        managed = self.save()
        protected = [self.root / "transcripts" / "old-short-message.txt",
                     self.root / "exported.txt", managed.parent / "manual.txt",
                     managed.parent / f"dictation_{uuid4().hex}.txt",
                     managed.parent / "nested" / "message.txt"]
        for path in protected:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("Keep me", encoding="utf-8")
        history.cleanup_dictation_history(self.root, "2_days", now=CREATED + 3 * DAY)
        self.assertFalse(managed.exists())
        for path in protected:
            self.assertEqual(path.read_text(encoding="utf-8"), "Keep me")

    def test_export_over_tracked_autosave_is_permanent(self):
        path = self.save()
        history.preserve_dictation_transcript(path)
        path.write_text("Explicitly saved", encoding="utf-8")
        history.cleanup_dictation_history(self.root, "2_days", now=CREATED + 3 * DAY)
        self.assertEqual(path.read_text(encoding="utf-8"), "Explicitly saved")
        self.assertEqual(history._read_index(path.parent), {})

    def test_changed_file_is_preserved_and_missing_file_is_forgotten(self):
        changed, missing = self.save(), self.save()
        changed.write_text("Edited in another program", encoding="utf-8")
        missing.unlink()
        self.assertEqual(history.cleanup_dictation_history(self.root, "2_days", now=CREATED + 3 * DAY), [])
        self.assertTrue(changed.exists())
        self.assertEqual(history._read_index(changed.parent), {})

    def test_locked_file_is_retried_on_next_cleanup(self):
        path = self.save()
        with patch.object(Path, "unlink", side_effect=PermissionError("locked")), self.assertLogs(history.logger):
            self.assertEqual(history.cleanup_dictation_history(self.root, "2_days", now=CREATED + 3 * DAY), [])
        self.assertTrue(path.exists())
        self.assertIn(path.name, history._read_index(path.parent))
        self.assertEqual(history.cleanup_dictation_history(self.root, "2_days", now=CREATED + 3 * DAY), [path])

    def test_corrupt_or_traversing_index_cannot_delete_files(self):
        path = self.save()
        index = path.parent / history._INDEX_NAME
        payloads = ["not JSON", "[]", json.dumps({"version": 1, "files": {"../outside.txt": {}}})]
        valid = json.loads(index.read_text(encoding="utf-8"))
        for created in (float("nan"), float("inf"), -1, 10**1000, True, "old"):
            valid["files"][path.name]["created_at"] = created
            payloads.append(json.dumps(valid))
        for payload in payloads:
            with self.subTest(payload=payload[:100]):
                index.write_text(payload, encoding="utf-8")
                with self.assertLogs(history.logger):
                    self.assertEqual(history.cleanup_dictation_history(self.root, "2_days", now=CREATED + 3 * DAY), [])
                self.assertTrue(path.exists())
                with self.assertRaises(OSError):
                    history.preserve_dictation_transcript(path)

    def test_registration_failure_keeps_recovery_copy_without_expiration(self):
        with patch.object(history, "_write_index", side_effect=PermissionError("read only")), self.assertLogs(history.logger):
            path = self.save()
        self.assertEqual(path.read_text(encoding="utf-8"), "A quick message")
        self.assertEqual(history.cleanup_dictation_history(self.root, "2_days", now=CREATED + 3 * DAY), [])
        self.assertTrue(path.exists())

    def test_concurrent_saves_do_not_lose_tracking(self):
        with patch.object(history.time, "time", return_value=CREATED), ThreadPoolExecutor(max_workers=4) as pool:
            paths = list(pool.map(lambda n: history.save_dictation_transcript(self.root, str(n)), range(12)))
        self.assertEqual(len(history._read_index(self.root / "dictations")), 12)
        deleted = history.cleanup_dictation_history(self.root, "2_days", now=CREATED + 3 * DAY)
        self.assertEqual(set(deleted), set(paths))

    def test_cleanup_rejects_reparse_points_and_does_not_recurse(self):
        path = self.save()
        original = Path.lstat

        def reparse(target, *args, **kwargs):
            if target == path:
                return SimpleNamespace(st_mode=0o100644, st_file_attributes=0x400)
            return original(target, *args, **kwargs)

        with patch.object(Path, "lstat", reparse), self.assertLogs(history.logger):
            self.assertEqual(history.cleanup_dictation_history(self.root, "2_days", now=CREATED + 3 * DAY), [])
        self.assertTrue(path.exists())

    def test_active_transcription_service_routes_only_dictation_to_history(self):
        service = FastTranscriptionService()
        model = MagicMock()
        model.transcribe.side_effect = lambda *a, **kw: (
            iter([SimpleNamespace(text=" A quick message ", start=0, end=1)]),
            SimpleNamespace(language="en", duration=1),
        )
        with patch.object(service, "_get_model", return_value=model), patch.object(service, "_estimate_duration", return_value=1):
            for is_dictation in (False, True):
                request = TranscriptionRequest(self.root / "audio.wav", self.root, "en", "base",
                                               show_timestamps=False, is_dictation=is_dictation)
                result = service.transcribe(request)
                self.assertEqual(result.transcript_path.parent,
                                 self.root / ("dictations" if is_dictation else "transcripts"))
                self.assertEqual(result.transcript_text, "A quick message")
                self.assertEqual(result.transcript_path.read_text(encoding="utf-8"), result.transcript_text)


if __name__ == "__main__":
    unittest.main()
