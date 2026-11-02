# scribe — Android lecture recorder, first test version

Independent native Android app for Mara's Galaxy Tab S6 Lite 2022 (SM-P619,
Snapdragon 720G, 4 GB RAM). Minimum Android 12 / API 31. The Windows application
remains in `../PC`; this project does not package or modify its recordings,
transcripts, settings or Python environment.

## Included

- Puffin-and-quill launcher/splash identity and turquoise (#00CED1), mint and
  blue colours. Settings offers a saved Dark theme switch; light remains the
  default. Source artwork and palette notes are in `design/`.
- English and Romanian UI, selectable in Settings. Other device languages fall
  back to English. Lecture language is a separate setting; automatic detection
  is the default.
- Library/Settings selects **Lecture** (the default) or **Compatibility** for new
  recordings/imports. Lecture uses the bundled Parakeet TDT v3 INT8 model and
  automatically recognises 25 European languages including Romanian and English.
  Compatibility keeps the prior Whisper model and fixed-language selector.
  Existing lectures keep their saved engine. More → Transcribe again creates a
  separate transcript from all retained audio, keeping original edits and marks.
- In Compatibility, the lecture-language shortcut at the top of Library exposes the same choices
  before recording/importing. Auto pins a detected language when a window at
  least eight seconds long returns non-repetitive text with sufficient confidence;
  the saved choice survives pause/restart. Existing finished text is unchanged.
- Lecture detail shows committed audio progress as a percentage and a separate
  animated working indicator with the current section and elapsed work time.
  Lecture mode always retains audio and underlines numeric text for checking.
  Confidence checks cannot establish the correctness of medical terms/numbers.
- Settings > Help / Setări > Ajutor explains recording, transcription, organising,
  playback, corrections, import/export, audio deletion and APK updates in short,
  personal notes for Mara. The guide is included in both languages and themes.
  App text uses direct sentences without em dashes.
- Settings > Patch notes / Setări > Noutăți keeps a short, versioned update
  history in both languages and themes. A compact, scrollable window shows the
  latest note once on an idle app opening after an update (or first installation).
  It includes instructions for new features. Opening old
  notes does not reset the latest-version marker; open windows survive recreation.
  Each update keeps its requested closing in both languages. The current note
  uses `With love` followed by `Your Danny~`; earlier notes keep their old closing.
- Start recording immediately, with no course required. Open a lecture to assign
  an existing course or create one afterwards. Courses can be renamed.
- Automatic localised course/date/time titles; optional custom titles.
- Library, course/date filters, full-text transcript search, rename and deletion.
- Start/pause/resume/stop, input level, elapsed time, background microphone
  service and notification controls. Audio time excludes pauses.
- ★ stores an audio timestamp immediately, without interrupting recording.
  The corresponding passage is highlighted once that part is transcribed.
- Continuous mono AAC recording at 16 kHz / 64 kbit/s. The encoder stays open
  while M4A containers roll over every 60 seconds; no microphone restart at
  chunk boundaries. Finalised files are synced before queueing.
- Recording takes priority: this first build transcribes **after recording
  stops**, using one worker, with a cached model across queued sections.
- The Lecture INT8 model is included (670,478,772 bytes total), alongside the
  Compatibility `base-q5_1` model (59,707,625 bytes). The larger APK/extracted
  models need more storage. Lecture mode uses about 1 GB of working memory and
  checks free memory before loading. See `TRANSCRIPTION.md` for the model/runtime
  revisions, licensing, cancellation and recovery behavior.
  The app has **no INTERNET permission** and requires no account or training.
- Streaming audio import through Android's document picker. WAV, MP3, M4A,
  AAC, FLAC and OGG depend on available Android decoders. The original remains
  untouched; a persisted read grant is used instead of copying a large file.
- Import and recording processing use bounded 30-second mono PCM windows with
  two seconds of overlap. Lecture mode merges a short final tail using at most
  38 seconds of buffered PCM. Contiguous recording files feed one recognition stream,
  avoiding a separate recognition call for every file's short tail. Long sources
  seek near the saved checkpoint on retry. Window text and every affected source
  checkpoint commit atomically; corrections remain intact.
- Recognized text is never supplied as the next window's initial prompt. The
  CPU setting remains three threads (per runtime session in Lecture mode).
  Compatibility windows may receive one beam-5 retry, no more often than once per
  six windows and subject to a cumulative retry-time gate. Failed optional retries
  preserve the first result and mark it for review; cancellation still stops work.
- Segment timestamps, text corrections, copy/share and UTF-8 TXT export through
  Android's file picker. Tap a passage to listen; hold it to edit. Playback
  continues to the next retained recording chunk.
- **Report a problem** on a lecture prepares a local ZIP, with a review screen
  before Android sharing (choose WhatsApp and the recipient) or Save a copy.
  Choose the whole lecture, a passage or a time range, an issue category, notes
  and expected words. Audio and transcript attachments are opt-in. Failed and
  empty transcripts can also be reported. Time-range audio is actually cropped;
  full-lecture audio retains its original encoding. Reports are capped at 256 MiB.
- New recordings/processing collect bounded, content-free timing/error records
  and sampled device conditions. Original recognised passage text is preserved
  separately from corrections. Upgrades from database versions 1/2 to version 3
  retain existing data; legacy originals and historical timings are explicitly
  unavailable. Version 3 persists detected language and quality-review state.
  Version 4 stores the engine without changing existing jobs or checkpoints.
- SQLite WAL with FULL synchronous writes. Window text and its checkpoint are
  committed together. Retrying an already committed window preserves edits.
- Crash recovery resets incomplete processing to ready; interrupted captures
  keep completed sections. An open M4A segment may be incomplete after a force
  kill or power loss; this build does not claim lossless recovery of that file.
- Available-space reserve and thermal checks; errors preserve source audio and
  saved text. Opening the app and choosing Continue resumes queued work.

## Audio retention

Lecture mode always retains audio. Compatibility retention is ON by default;
its optional deletion setting is snapshotted for **new Compatibility recordings**.
It never deletes imported originals. A recorded chunk
can be removed only after non-empty transcript results have been committed
successfully. Since version 0.1.5-test, deletion also requires the entire lecture
to finish without processing errors or quality-review warnings. Failed,
cancelled, empty or flagged results keep their audio. Checks cannot detect every
word error; leave automatic deletion off when recordings need review.
Completion and cancellation are serialised around deletion. The schema-v3 update
disables automatic deletion for existing lectures to preserve their remaining
audio; it does not restore previously deleted files or change the saved default
for new lectures.

Deleting audio also removes the ability to replay that section; text and
timestamps remain. Silence-only sections are conservatively retained and marked
for attention rather than silently treated as successful transcription.

Imported audio must remain accessible through the selected provider. For offline
use, choose a local file. Moving/deleting an external original or revoking its
grant may prevent later processing/playback; existing transcript text remains.

## Deferred as requested

No Google Play/Firebase distribution integration, in-app update mechanism,
automatic report uploads or telemetry. No live transcript mode, LLM summary, system/app
audio capture, desktop shortcuts, third-party text insertion, subtitle export,
speaker identification, user accounts or cloud sync. Automated developer tests
are separate from these deferred product features.

## Build on Windows

The toolchain stays in the ignored `.tools` directory. Bootstrap uses a verified
Temurin JDK 17, Gradle 8.11.1, Android plugin 8.9.2, SDK/build tools 35,
NDK 27.0.12077973 and CMake 3.22.1. Downloads use verified HTTPS and publisher
checksums; no global PATH changes are required.

```powershell
Set-Location D:\Dev\Recorder\App
python tools\bootstrap.py
python tools\prepare.py
.\build.ps1 -Tasks assembleDebug,testDebugUnitTest,lintDebug,assembleDebugAndroidTest,assembleRelease,lintRelease
```

`prepare.py` reuses a persistent signing key in `.tools/signing`. Both test and
release variants use that key. **Keep this directory private and back it up**;
it is intentionally excluded from Git. Never replace it to work around an
installation issue. There are no signing passwords in source files or command
arguments. Release APK: `app/build/outputs/apk/release/app-release.apk`.

The debug APK contains optimized C++ kernels too (`-O3`, with IEEE math preserved).
It includes arm64-v8a for the tablet and x86_64 for the local emulator. The
initial CPU cap is three threads; this is a conservative starting configuration,
not a benchmark-proven optimum for SM-P619.

## Tests and real-device limits

```powershell
.\test-emulator.ps1 -Serial emulator-5580
```

The script refuses physical devices. It installs the debug and separate test
APK on the selected emulator, grants microphone/notification permissions, and
fails explicitly if instrumentation reports failures. Tests cover deletion
rules, overlap/resampling, SQLite search/edit/recovery, long streaming imports,
actual native speech recognition of the public whisper.cpp JFK sample, recording
rollover/pause/screen-off, and both UI languages. The public sample is included
only in the separate instrumentation APK, never the main APK.

Emulator results do not establish microphone quality, sustained inference speed,
heat, battery life, Samsung background behaviour or lecture accuracy on SM-P619.
Those require tests on Mara's tablet, first with a short representative lecture
sample, then a 90–120 minute session. Her lecture language and Android version
still need to be confirmed. No performance result is inferred from the PC CPU.

## Source layout

- `app/src/main/java/ro/scribe/lectures`: UI, durable store, recording service,
  bounded decoder/resampler and JNI wrapper.
- `app/src/main/cpp`: JNI bridge and pinned whisper.cpp source.
- `app/src/main/res/values*`: English/Romanian text and Android resources.
- `app/src/main/assets`: public model and attribution only.
- `app/src/test`, `app/src/androidTest`: developer tests.
- `tools`, `build.ps1`, `test-emulator.ps1`: reproducible local build/test tools.

The existing Git repository remains at `D:\Dev\Recorder\.git` and can track
both `PC` and `App`. No nested Git repository or Git-directory move is required.

## Problem reports

See `REPORTS.md` for the tester workflow, archive contents and privacy limits.
Inspect a received ZIP on Windows without extracting it:

```powershell
python tools\inspect_report.py 'path\to\scribe-report-....zip'
python -m unittest discover -s tools -p test_inspect_report.py -v
```
