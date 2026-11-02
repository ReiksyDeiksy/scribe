# Problem reports

## For Mara

1. Open the lecture and tap **Report a problem / Raportează o problemă**.
2. Choose what went wrong. Select the whole lecture, a text passage or a time
   range. A time range works even if speech was omitted from the transcript.
3. For recognition problems, tick **Include the selected audio** and **Include
   the selected transcript**. Both are off initially. Add a short note and the
   correct words if useful; neither is required.
4. Tap **Prepare report**, review the selected scope, attachments, size and
   notes, then **Share report**. Choose WhatsApp and our chat.
   The recipient and final send remain your choice. **Save a copy** also writes
   the ZIP to a location selected with Android's file picker.

Stop recording or pause the lecture's transcription before preparing the report.
Preparation does not send anything or change the original recording, transcript,
corrections or retention preference. Missing/deleted audio is identified in the
preview and archive. It cannot be recovered by reporting it. A report without
audio or transcript can still describe a crash, slowdown or playback issue.

Prepared ZIPs are temporary app-cache files. They may be evicted by Android and
copies older than seven days are removed when preparing another report. Save an
external copy if needed. Copies saved or sent through another app have that
app's retention rules. Leaving the report screen during preparation cancels the
work; the draft is restored on activity recreation, but an interrupted build
may need to be prepared again. No report is labelled delivered by scribe.
Abandoned unfinished exports older than a day are cleaned up on the next export.

## Contents and scope

Each archive uses schema version 1 and includes:

- `README.txt`: a readable explanation, issue category, notes and expected text.
- `report.json`: report ID, selected lecture's ID/title/status/language, selection
  bounds, app/device information and export-time model settings, attachment
  sizes and SHA-256 checksums, missing-audio sections and diagnostic limits.
- `diagnostics.json`: selected lecture's available events. Window measurements
  include decode time, recognition time, processing/commit time, audio duration,
  RMS and returned/accepted segment counts. Other events record capture start/
  finish, model loading, chunk starts and processing errors. Samples include
  thermal status, battery/charging state, native/Java heap use and free storage.
  Since 0.1.5-test, windows also include the used/requested language, detection
  probability when detection ran, average token log probability, bounded beam
  retry time and review flags. These are diagnostic signals, not accuracy scores.
- Optional `transcript.txt` and `transcript.json`: selected overlapping passages.
  A passage overlapping a time range is included in full, as explained in the
  preview. JSON includes both current text and preserved original recognised
  text. Original text is null for legacy rows; an existing correction is never
  presented as a known original recognition result.
- Optional `audio/section-NNNNN.*`: full selections copy original files without
  recompression. Excerpts contain only the selected decoded samples, in mono
  16 kHz, 16-bit PCM WAV. Across recording-file boundaries there is one excerpt
  per source section, with lecture-time bounds in the manifest.

The archive does not contain the database, other lectures, source paths/URIs,
model weights, device serial/account identifiers or device-wide logcat output.
Diagnostic records do not contain speech/transcript text or exception messages.
Processing errors include exception class and stack locations. Notes, expected
words and optional attachments can contain personal lecture information, so the
user reviews and explicitly shares each report.

## Limits and implementation

- The app remains offline and has no INTERNET permission. Android's chosen
  receiving app handles transmission. There is no PC server, public port,
  background upload, WhatsApp account integration or automatic recipient.
- AndroidX Core 1.15.0 supplies the standard FileProvider. Its only shared root
  is `cache/reports/ready/`; partial files, source recordings and database files
  are outside that root. Sharing grants temporary read access to one ZIP using
  EXTRA_STREAM and ClipData. No write or prefix grant is issued.
  Its Apache 2.0 license and runtime-component attributions are included in the
  APK's `assets/legal/AndroidX-Apache-2.0.txt`.
- Export streams files on a worker and enforces a 256 MiB archive limit and the
  existing free-space reserve. Excerpts use bounded decoding, seek near the
  selected start and stop after the selected end. Failed/cancelled exports
  remove their partial outputs. Recording takes priority over report generation.
- Diagnostics are capped at 2,048 events per lecture and 8,192 in total. Older
  events may have been removed. Report-time device conditions/default settings
  are labelled separately from historical measurements. Measurements are samples,
  not exact peak process memory or a hardware performance guarantee.
- Database version 2 adds nullable original text and a diagnostics table without
  replacing existing lectures, audio paths, text, course assignments or marks.
  Version 3 adds persisted detected language and a conservative review flag.
  Existing lectures have automatic audio deletion disabled on upgrade, preserving
  remaining recordings. Review flags keep audio and do not discard recognized text.
- A ZIP is a portable evidence bundle, not an executable test or app backup.
  `tools/inspect_report.py` validates attachment sizes/checksums and summarizes
  available timings without extracting or executing the archive. Window timing
  totals include overlap/retries and are not total lecture processing time.

## Physical-device check before broader use

Install the signed update over the existing app. Create a short Romanian
recording, generate one passage report including audio/text, and send it through
WhatsApp as a file. Open the received ZIP on Windows, inspect it with the tool,
and listen to its excerpt. Check that only the selected interval was sent, that
the original recording and corrections remain, and that new timing records are
present. Repeat without attachments and with an unavailable audio source.

Emulator checks cannot establish WhatsApp's exact Samsung-device behavior,
classroom accuracy, sustained speed, battery use or three-hour capture reliability.
