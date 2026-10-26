# First Android test build — 22 October 2026

## Build and payload

- Android application ID: `ro.scribe.lectures`; version `0.1.0-test`, versionCode 1.
- Android 12 / API 31 minimum, target/compile SDK 35.
- Debug and signed release APKs built successfully. Debug/release Android Lint:
  **No issues found**. Two narrowly documented lint annotations cover inlined
  SQLite constants on API 31/32 and locally bundled locale switching.
- Release signer: RSA 3072-bit, APK v2 signature verified with Android apksigner.
- Signing certificate SHA-256:
  `c42170e8641cdf6309457bfc4850169d94a0e05fac47a82ab6b088d73d8ac44d`.
- Native arm64-v8a and x86_64 libraries: 16 KB LOAD alignment; Android zipalign
  validation with `-P 16` passed.
- Bundled model SHA-256 verified against the pinned publisher model.
- APK asset allowlist: public model and three MIT attribution files. No source
  recordings, transcript data, settings or signing keys in the APK.
- No INTERNET, broad storage, contacts, location or telephony permissions.
  Audio import/export uses explicit Android document-picker grants.
- Both English and Romanian are packaged locally. No cloud services, analytics,
  in-app reporting or update/distribution SDKs are included.

Run `python tools/verify_apk.py <path-to-apk>` for a repeatable payload audit.
The final APK digest and byte size are recorded in `artifacts/apk-audit.json`
and `artifacts/SHA256SUMS.txt`.

## Automated verification

**6 JVM unit tests passed:** deletion preconditions, overlap merging, safe FTS
query construction, lecture clock formatting, streaming resampling at integer
and fractional ratios, and rejection of punctuation/music-only results as words.

**7 Android instrumentation tests passed, 94.359 seconds total**, on a local
Android 15 / API 35 x86_64 tablet emulator with four virtual CPUs, 3 GB RAM and
WHPX acceleration:

1. Microphone capture, ★ timestamp, pause/resume, screen-off continuation,
   rolling beyond 60 seconds into multiple playable M4A containers, and retention
   of unrecognised audio even with successful-audio deletion enabled.
2. A generated 65-second, 48 kHz stereo WAV import streamed into bounded
   overlapping windows; seek/resume and cancellation preserve the original file.
3. Actual native Whisper inference of the public JFK reference sample, including
   Android media decoding and assertions on recognised English words.
4. Actual service queue: successfully transcribed owned audio removed only after
   saved text; malformed owned audio retained on failure.
5. SQLite window idempotency, FULL synchronous mode, marker association,
   processing checkpoint recovery and cascade/search cleanup.
6. Course assignment after recording plus Romanian full-text search and index
   updates after a text correction.
7. English/Romanian activity rendering, automatic localised titles, retained
   transcript content and highlighted passages.

UI capture was repeated with a rendering-settle delay and the resulting PNGs
were visually inspected. The signed **release** variant also passed actual
speech recognition and both-language UI instrumentation (2 tests, 18.299 s).
The final safety/clipboard changes were rechecked with JVM tests, native/service
queue and UI instrumentation; see `instrumentation-final.log`.

Manual release checks: installation, opening settings, switching English to
Romanian through the actual picker, system Back navigation, and visual inspection
of both settings layouts. The start screen uses the app's dark background.

## Performance evidence and limits

The original native Debug build compiled ggml tensor loops without optimization.
This was confirmed in generated Ninja compile flags. The same speech-test method
on the same emulator took 157.830 s before correcting native compile options and
9.611 s afterwards. Final compile flags include `-O3`; fast-math is not enabled.
This is a development comparison from test runs, not a controlled benchmark or
a prediction for the Samsung tablet.

Other bounded-cost choices: a single transcription worker, model reuse across
queued sections, mono AAC capture, rolling containers, bounded decode windows,
checkpoint seeking for retries, indexed text search and unchanged-list refresh
avoidance. These do not imply that tablet inference will keep up with speech.

## Not yet established on Mara's SM-P619

- Real microphone/far-field lecture quality, including Romanian lecture speech.
- Sustained speed, peak RAM, heat and battery cost over a 90–120 minute lecture.
- Samsung-specific background restrictions and actual installed Android version.
- Real incoming-call/microphone-revocation behaviour.
- Deliberate full-filesystem/power-loss fault injection. Storage/retention paths
  are implemented; an open, unfinished M4A segment can be lost after a force kill.

Emulator capture uses a virtual microphone. The real ASR tests use a public audio
file, so they do **not** prove the tablet microphone-to-transcript experience.
Live transcription, summaries, Play/Firebase distribution and app-side test
reports remain outside this build.

## Evidence files

- `build.log`, `package.log`: build output (local, ignored by Git).
- `instrumentation.log`, `instrumentation-ui.log`, `instrumentation-release.log`,
  `instrumentation-final.log`: developer test results (local, ignored by Git).
- `app/build/test-results/testDebugUnitTest/`: JVM test XML.
- `artifacts/qa/`: English/Romanian screenshots with synthetic UI test content.
- `artifacts/apk-audit.json`, `artifacts/SHA256SUMS.txt`: final APK verification.

The desktop project under `../PC` was not edited. Its move was already present
when this task began. The Git directory remains at the shared parent root.
