import hashlib
import json
import io
import unittest
import zipfile

from inspect_report import inspect_report


class ReportInspectionTest(unittest.TestCase):
    def make_report(self, *, extra=None, audio=b"audio", checksum=None, text=False):
        stream = io.BytesIO()
        report = dict(schema_version=1, report_id="test", problem="wrong_text", note="\x1b[31mnote",
                      expected_text="", app_at_export={}, lecture={}, selection={}, audio_requested=True,
                      audio=[dict(file="audio/section-00000.wav", bytes=len(audio),
                                  sha256=checksum or hashlib.sha256(audio).hexdigest())],
                      missing_audio=[], transcript_included=text)
        with zipfile.ZipFile(stream, "w") as archive:
            archive.writestr("report.json", json.dumps(report))
            archive.writestr("diagnostics.json", "[]")
            archive.writestr("audio/section-00000.wav", audio)
            if extra:
                archive.writestr(extra, "unexpected")
        stream.seek(0)
        return stream

    def test_validates_audio_without_extracting(self):
        self.assertEqual(inspect_report(self.make_report())["audio_files_verified"], 1)

    def test_rejects_changed_audio(self):
        with self.assertRaises(ValueError):
            inspect_report(self.make_report(checksum="0" * 64))

    def test_rejects_unsafe_and_unexpected_entries(self):
        for name in ("../secret", "C:/secret", "audio/../../secret", "run.exe", "transcript.txt"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                inspect_report(self.make_report(extra=name))


if __name__ == "__main__":
    unittest.main()
