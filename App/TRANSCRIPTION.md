# Lecture recognition (0.2.0-test)

New recordings/imports default to **Lecture** mode: NVIDIA Parakeet TDT 0.6B v3,
INT8 ONNX conversion, sherpa-onnx 2.0.1 / ONNX Runtime 1.28.2, CPU with at most
three threads per runtime session. Actual OS thread count is larger. It supports
25 European languages including Romanian and English.
This model identifies languages automatically; the app does not claim to force
a language or expose a detected label or calibrated accuracy estimate.
**Compatibility** retains the previous Whisper model, language selector and
bounded retry policy below. Turkish requires Compatibility.

The mode chosen in Library/Settings is saved on each new lecture. Database v4
adds `recognition_state.engine`, defaulting existing jobs to `whisper`, without
rewriting text or changing partial checkpoints. The previous four-argument
`Store.createLecture` API still creates Compatibility jobs; app recording/import
uses the overload with an explicit mode. A worker closes one model before loading
another, never caching both large allocations together.

Lecture model files are bundled, copied on the worker using bounded buffers,
SHA-256 verified and individually committed by rename. Interrupted partials are
removed. Loading checks available memory (at least 1280 MiB and no low-memory
signal); failure keeps audio and reports an actionable error. The larger APK and
extracted models require substantially more storage than the old build.

Lecture mode uses greedy TDT decoding, without repeated beam passes. Its word
timestamps remove committed overlap before words are grouped into passages of
about ten seconds or a sentence. Numeric text is retained exactly as recognized
and underlined for review. The app never guesses that `26` should mean `2–6`,
or silently replaces medical terminology using the evaluation slides.

Eight seconds of PCM lookahead lets the final window include a short tail
(maximum 38 seconds); non-final windows are still 30 seconds with two seconds
of overlap. Buffer use is bounded. Compatibility retains its original 30-second
window behavior. Source boundaries, checkpoints and cancellation use the same
existing transactional pipeline. The new runtime cannot abort an active native
call: pause waits for that bounded call to finish, then discards its uncommitted
result. Model extraction and audio decoding remain cancellable.

Lecture results always require review and always retain audio, irrespective of
the Compatibility deletion preference. Model scores do not prove the accuracy of
medical terms, numbers or negations. Reports record the per-lecture engine and
historical runtime; Whisper-specific confidence/detection values are null. Raw
TDT token scores are not collected or interpreted as accuracy. Returned
units are words in Lecture mode and segments in Compatibility, so segment counts
are not an accuracy metric or directly comparable between engines.

**More → Transcribe again** copies all retained source audio on a worker and
creates a separate lecture with the selected mode, course and copied marks. It
does not copy old recognized words into the recognizer or replace corrections.
Missing sources, cancellation and low storage fail without altering the original.
Copies are staged in app cache, then moved into a new owned directory with the
new database rows committed together. A process kill during staging can leave
evictable cache files; a kill between file renames and DB commit can leave orphan
copy files, but cannot delete the original audio or transcript.

Public dependencies, hashes, preparation and attribution:

- `app/src/main/assets/lecture-model.json`: revision and four model digests.
- `tools/lecture-runtime.json`: release archive and per-ABI runtime digests.
- `tools/prepare_lecture.py`: verified public downloads and exact-entry extraction.
- `assets/legal/lecture-NOTICE.txt`: NVIDIA CC BY 4.0 attribution and conversion;
  sherpa-onnx Apache 2.0 and ONNX Runtime MIT/third-party notices are bundled.
- [Original model](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3),
  [Android-capable runtime](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/nemo-transducer-models.html).

No user audio is used by preparation or sent to a service. The APK has no Internet
permission. Private evaluation fixtures and outputs remain ignored local files.
Measured results and the limits of the three-hour replay are in `VALIDATION.md`.

# Compatibility pipeline (introduced in 0.1.5-test)

The existing multilingual base Q5 model, CPU backend, three-thread cap, audio
format and 60-second recording rollover remain. No network service is involved.

## Recognition windows and recovery

`LectureDecoder` reads contiguous recording sources through the existing bounded
decoder and removes its local decode overlap before feeding `PcmWindows`.
The shared buffer emits 30-second windows with two-second recognition overlap;
a recording-file boundary does not flush it. Missing/error/noncontiguous sources
separate groups. Only the lecture/group's actual end flushes the final tail.

`Store.saveSequenceWindow` inserts text and advances all affected source
checkpoints in a single FULL-synchronous SQLite transaction. A cross-file text
segment belongs to the source containing its start; playback continues through
retained subsequent sources. Source completion checks overlapping text as well
as text whose start belongs to that source.

Resume starts two seconds before the committed checkpoint. A retained preceding
source supplies overlap when needed at a file boundary. Already committed text,
manual corrections, original recognition text and marks are not replaced.
Empty fully processed error sections can be retried from their beginning;
valid partial checkpoints remain intact. No audio tail is discarded to save time.

## Language and decoding

- Explicit lecture language is captured when recording/importing. The Settings
  selector remains and Library adds a visible shortcut to the same preference.
- Auto detects on a window, and pins the language only when an audio window at
  least eight seconds long returns nonempty, non-repetitive text and detection probability
  >= 0.5. The stored language survives cancellation/restart. Uncertain/empty
  fragments do not establish a permanent language. This heuristic can still err;
  use explicit Romanian/English when the language is known.
- Recognition never receives previous recognized text as an initial prompt.
  Previous text is used only to trim an overlapping beginning after recognition.
- Greedy decoding runs first. Low average token log probability (< -0.50), long
  repetition, or sparse timestamp coverage at nontrivial RMS can request a beam-5
  retry on windows at least eight seconds long.
- At most one retry occurs per six windows. A cumulative retry-time gate limits
  later retries; a single allowed retry can exceed the gate's allowance. There
  is no unlimited temperature schedule. Cancellation interrupts either pass.
- The result with the better confidence/coverage/repetition score is retained.
  A failed optional retry keeps the first result, marks review and records a
  content-free failure. Scores are heuristics, not calibrated accuracy measures.

## Progress and retention

The main progress bar represents committed audio duration. The animated working
strip and section/elapsed-time label remain active during recognition or a retry.
They show activity before the first committed section, without fabricating an
ETA or percentage for an unfinished native call.

Uncertain results are saved with a review warning. Audio deletion requires full
lecture completion, no processing errors and no review flag. A flag is deliberately
sticky; editing some words is not proof that the whole recording was reviewed.
Automatic checks cannot detect every word error; leave audio deletion off to
retain recordings for later checking. Imported originals are never deleted.

Database v3 adds `recognition_state` with detected language and review state.
The v1/v2 upgrades preserve text, edits, search, marks and checkpoints, and disable
automatic deletion for existing lectures to retain their remaining recordings.
The saved default for new lectures is unchanged. Deleted old audio cannot be
recovered. Existing finished transcripts are not automatically reprocessed.

## Diagnostics and reproducible evidence

Window diagnostics include used/requested language, detection probability only
when detection ran, average token log probability, retry time and review flags.
No speech text is logged in diagnostics. Model/pipeline settings are recorded at
source-group start; reports continue using portable schema 1 with additional
lecture metadata for detected language/review state.

`PrivateReplayInstrumentedTest` is skipped by default. To run a local comparison,
first validate a report with `tools/inspect_report.py`, then copy only its
manifest and audio into the test device's app external-files `qa-input-report/`
directory. Explicitly pass `-e replay true -e output_tag <label>` and select that
test. It creates a temporary lecture with deletion off, runs the real queue,
saves `qa-<label>.json`, checks retained files and removes its temporary lecture.
It does not embed private fixtures in the app/test APK or transmit them elsewhere.

The release evidence in `VALIDATION.md` distinguishes emulator measurements from
Samsung speed, accuracy and sustained three-hour reliability. The model still
misrecognizes specialist vocabulary and requires review.
