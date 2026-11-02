# Balanced transcription (0.1.5-test)

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
