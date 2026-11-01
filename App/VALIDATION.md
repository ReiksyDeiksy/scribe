# Personal message sign-off — 1 November 2026

Version `0.1.4-test`, versionCode `5`, appends exactly `Love ya, Mara <3` to
every patch-note window (new and historical) and the Help closing message.
One non-translatable resource supplies the same English text in both locales.
The latest history entry records this update and keeps the reporting steps.
Release instructions now preserve this preference for future messages.

Verification:

- Signed release build and lint passed (**No issues found**); **7/7 JVM tests**
  passed. No dependency, permission, database or speech-processing changes.
- Final signed APK: **3/3 Android UI tests** passed on the Android 15 tablet
  emulator, 24.686 seconds. Checks cover the exact sign-off in current and older
  notes in both languages/themes, version matching, once-per-update behavior,
  recreation, navigation and the visible Help footer after scrolling.
- The same three checks passed again at 1080 x 2400 / 420 dpi / 1.3 font scale:
  **3/3**, 25.837 seconds. Tablet/phone captures were visually reviewed and the
  emulator display/font settings restored.
- Installed over version 4 without uninstalling or clearing data. Existing
  database, preferences and file hashes matched immediately before/after install.
- Existing signing certificate, APK signature, native/ZIP 16 KiB alignment,
  pinned model checksum and payload allowlist passed. No private media or keys.

Prepared update:
`artifacts/private-release/scribe-0.1.4-test-vc5-signoff-d8886551/scribe-0.1.4-test-vc5.apk`
(70,585,754 bytes). SHA-256:
`d8886551cdc4f8ddc3071222c3c9d383c546712609c2d6394eb03abdcbd9592d`.
Installation guide, checksum, public signing metadata and payload audit are beside
the APK. Previous packages are unchanged; no sending/publishing was performed.
Physical Samsung testing remains separate from these emulator checks.

Logs: `signoff-build.log`, `signoff-tablet.log`, `signoff-phone.log`, `signoff-audit.log`,
`signoff-signature.log`. UI and upgrade hash evidence: `artifacts/qa/signoff/`.

---

# Patch notes and update history — 1 November 2026

Version `0.1.3-test`, versionCode `4`, adds Settings > Patch notes / Setări >
Noutăți, with four versioned notes. Selecting a version opens a compact,
scrollable dialog. The latest note appears once on an idle app opening after
an update or first installation. It includes steps for problem-report sharing,
including for users who skipped version 3. The preference and any open dialog
survive activity recreation. Recording, transcription, database schema and report
sharing are unchanged.

Personal instructions use the author's voice ("send me", "our chat") in both
languages, including help and older notes. No author-name signature is appended.

`AGENTS.md` and the distribution checklist now require short localized notes
and usage instructions for every future user-visible update. A release check
asserts that the newest note matches the installed app version.

Verification:

- Debug/release builds and lint passed. Both lint reports: **No issues found**.
- JVM tests: **7/7 passed**.
- Full signed-release Android regression: **19/19 passed**, 129.138 seconds,
  on the Android 15 tablet emulator before the final wording correction.
  This includes the previous capture,
  streaming decode, actual English recognition, queue/retention, database,
  report-sharing, help/theme and transcript checks.
- Patch-note instrumentation: **2/2 passed** on the signed release APK on the
  Android 15 tablet emulator. Covers existing/missing seen-version preferences,
  once-per-version behavior, active-lecture deferral, window recreation, history,
  Settings/back navigation, English/Romanian and both themes.
- The same two patch-note tests passed again before the wording correction at 1080 x 2400,
  420 dpi and 1.3 font scale: **2/2**, 15.513 seconds. Tablet and phone captures
  were visually reviewed; long notes scroll and the dialog actions stay visible.
  The emulator display/font settings were restored afterwards.
- After the wording correction, the final APK passed the affected patch-note,
  help and report checks: **11/11**, 36.88 seconds, in both languages/themes.
  Release build/lint, signing, payload and alignment checks passed again.
- The emulator's existing signed version 3 installation was upgraded in place to
  version 4. All saved database, preferences and file hashes matched immediately
  before/after installation; no uninstall or data clearing was used.
- APK signature, existing signing certificate, native/ZIP 16 KiB alignment,
  pinned speech-model checksum and asset/media/key allowlist passed. No new
  dependencies, permissions, network connections or automatic sharing.

Prepared update:
`artifacts/private-release/scribe-0.1.3-test-vc4-patch-notes-1b852ec7/scribe-0.1.3-test-vc4.apk`
(70,583,674 bytes). SHA-256:
`1b852ec76a258abedfbc1575ff7c0d1341a8b2b062d35a1b99569b6ad77f046c`.
Installation guide, checksum, public signing metadata and APK audit are beside it.
No sending or publishing was performed. Physical Samsung testing is still needed;
this update does not establish Romanian accuracy or three-hour performance.

Logs: `patch-build.log`, `patch-build-final.log`, `patch-window-tablet.log`,
`patch-regression.log`, `patch-window-phone.log`,
`patch-signature.log`, `patch-apk-audit.log`. Screenshots and upgrade hash evidence
are in `artifacts/qa/patch-notes/`. Final wording checks: `patch-wording-build.log`,
`patch-wording-regression.log`, `patch-wording-audit.log`,
`patch-wording-signature.log`.

---

# Problem reports and file sharing — 1 November 2026

Version `0.1.2-test`, versionCode `3`, adds a lecture-level report screen with
whole-lecture/passage/time-range selection, opt-in audio/text, notes and expected
words, an attachment/size preview, Android sharing and document-picker saving.
English/Romanian and both themes are supported. WhatsApp is selected by the user
in Android's sharesheet; the app never sends automatically.

New processing stores bounded timing/error measurements without transcript text.
Database version 2 preserves original recognised passage text separately from
edits; its non-destructive version-1 migration leaves legacy originals unknown.
The Windows inspector validates report archives without extracting/executing them.

Verification:

- Debug/release builds and lint passed. Both lint reports say **No issues found**.
- JVM tests: **7/7 passed**. Windows report-inspector tests: **3/3 passed**.
- Full signed-release Android regression: **17/17 passed**, 114.507 seconds, on
  the Android 15 tablet emulator. This covers capture/rollover/pause/screen-off,
  streaming import, actual English recognition, queue/retention, store/search,
  themes/help, bilingual UI, and eight report-specific tests.
- Report checks include exact PCM excerpt boundaries across decode windows and
  recording sections, actual end time for unknown-duration inputs, unchanged
  full audio, original/corrected text, attachment opt-in, missing audio, failed
  export cleanup, recording priority, legacy migration and recreated drafts.
- A receiver activity in the **separate test APK/process** opened the shared ZIP.
  Tests verified no access before granting the URI, no write grant, a non-exported
  provider, rejection of traversal and inability to share unrelated private files.
- The final APK, including the third-party license asset, passed the eight report
  tests again: **8/8**, 9.986 seconds, at 1080 x 2400 / 420 dpi / 1.3 font scale,
  in English/Romanian and light/dark themes. Emulator display settings restored.
- A real synthetic Android report was transferred to Windows and successfully
  checked with `tools/inspect_report.py`, including its audio checksum and scope.
- Release signer matches the existing certificate. APK signature v2, native/ZIP
  16 KiB alignment, pinned model SHA-256 and asset allowlist all passed. The
  allowlist now also permits the Apache license and validated compiled AndroidX
  baseline-profile assets. No INTERNET permission or private media/key payload.

Prepared update:
`artifacts/private-release/scribe-0.1.2-test-vc3-reports-dfa18077/scribe-0.1.2-test-vc3.apk`
(70,557,462 bytes). SHA-256:
`dfa18077f1fc46cc87a4de365dc4d9241497518b9bd3412ad64ab7f3ff9c793e`.
The folder also contains the installation guide, checksum, release metadata,
signature evidence and payload audit. No delivery or publishing was performed.

Logs: `report-final-build.log`, `report-release-regression.log`,
`report-release-phone.log`, `report-package-build.log`. UI captures and the
synthetic ZIP are under `artifacts/qa/reports/`. Real Samsung WhatsApp sending,
Romanian accuracy and three-hour performance still require physical-device tests.

---

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

## Turquoise / puffin visual update — 1 November 2026

- Selected puffin-and-quill concept 09 added to the header, Android splash, and
  adaptive launcher icon on a #00CED1 background. A monochrome silhouette is
  included for notifications and Android themed icons. The circular adaptive
  icon was inspected in Android App info; beak and quill remain visible.
- Shared colour resources now provide turquoise actions, pale mint backgrounds,
  white cards, dark ink text, and the supplied mint/blue supporting colours.
  Dialogs, spinners, switches, and system bars use the matching light theme.
- Navigation gained icons and spacing. Search and cards have rounded outlines.
  Transcript action rows grow to fit translated labels; no fixed-height text
  clipping remains in the inspected English/Romanian phone layouts.
- Recording, import, transcription, retention, storage and export logic were
  not changed. The service change selects the monochrome notification drawable.
  No new permissions, libraries, or network access were introduced.

Validation for this update:

- Debug and signed release builds succeeded. Final debug/release lint reports:
  **No issues found**. Final JVM suite: **6/6 passed**.
- Existing full Android regression suite: **7/7 passed**, 96.915 seconds, on the
  Android 15 emulator, covering capture/pause/rollover/screen-off, streaming
  import, actual speech recognition, queue/retention, store and bilingual UI.
- After the final icon sizing, navigation spacing, and translated-button fixes,
  the existing bilingual UI test was repeated on the signed release at a
  phone configuration (1080 x 2400, 420 dpi): **1/1 passed**, 5.504 seconds.
  The final signed release also passed the same test at the original tablet
  configuration (800 x 1280, 160 dpi): **1/1 passed**, 5.177 seconds.
  A separate short virtual-microphone check confirmed the final recording,
  pause/resume label, and stop controls. Settings and its language picker were
  also opened and inspected.
- Checked contrast ratios: ink on primary turquoise **7.29:1**; dark turquoise
  text on white **6.14:1**; secondary text on the mint canvas **6.07:1**; ink on
  aquamarine **11.63:1**.
- Final release signature and 16 KB zip/native alignment verified. The APK
  payload audit passed: pinned speech-model digest, expected assets only,
  both supported ABIs, no private media or signing-key payload.

Installable output: `artifacts/scribe-turquoise.apk` (65,549,464 bytes).
SHA-256: `aa6c5a5f931b8fc2d20b044c62079111a9d5cec19eec0a10824ed34c7011aecc`.
Its companion checksum is `artifacts/scribe-turquoise.sha256` and the payload
audit is `artifacts/apk-audit.json`. Build/test logs use the `design-` prefix.
Real emulator screenshots are under `artifacts/qa/turquoise/`; phone captures
from the final signed release use the `phone-` prefix. They contain synthetic
test content, not real lecture transcripts.

The update has not been installed or checked on the physical Samsung tablet.

Caption follow-up: the English and Romanian tagline resources now both show
`Hello there my Puffin! ^w^`. The release APK above was rebuilt, release lint
reported no issues, and its payload audit passed. Earlier screenshots and UI
test results predate this text-only follow-up.

## Optional dark theme — 1 November 2026

- Added a saved Dark theme / Temă întunecată switch in Settings. Light remains
  the default. Android's app-local night mode applies the choice to native
  widgets, dialogs, splash, system bars and activity resources.
- The five brand colours and turquoise launcher background are identical in
  both themes. Dark surfaces and light text have separate night resources;
  lettering on turquoise primary buttons stays dark in both modes.
- Final debug/release builds and lint passed (no issues); JVM tests: 6/6 passed.
  Full Android regression suite: 8/8 passed in 102.158 seconds, including the
  new settings-toggle, activity-recreation and saved-choice check.
- The final signed release passed the theme test separately (1/1, 4.529 s).
  A manual UI check confirmed the choice survives force-stop and full process
  relaunch. English/Romanian transcript UI in dark mode also passed (1/1,
  5.307 s). Dark Settings, Library, transcripts and native date picker were
  captured and visually inspected on the Android 15 tablet emulator.
- Checked text contrast ratios in dark mode: primary-button text 7.29:1,
  body text on cards 13.08:1, secondary text on background 9.30:1,
  turquoise text on tinted surfaces 5.84:1, highlighted text 7.30:1.
- The updated signed APK is artifacts/scribe-turquoise.apk with its checksum
  and payload audit alongside it. Signature and 16 KB zip/native alignment
  passed. No permissions, dependencies or audio-processing logic changed.

Theme screenshots: artifacts/qa/dark-theme/. Build and test logs use the
dark-theme- prefix. Physical Samsung tablet verification is still required.

## Personal offline help - 1 November 2026

- Added Settings > Help / Setări > Ajutor, with a personal greeting for Mara
  and ten short chapters covering recording and marks, transcription, library
  and courses, playback and corrections, imports, export/sharing/deletion,
  settings, audio retention, troubleshooting and direct APK updates.
- Help uses the existing light/dark palette and local English/Romanian resources.
  The back button and Android Back return to Settings. The help screen survives
  activity recreation; Settings stays selected in the bottom navigation.
- App copy contains no em dashes. The two personal sign-offs use a newline
  before Daniel instead. Checked both XML string sets and the compiled release
  resource values for U+2014 after the final rebuild.
- Version is `0.1.1-test`, versionCode `2`. Package name, private signing key,
  permissions, database schema and audio-processing behaviour are unchanged.

Validation:

- Debug/release builds and both lint checks passed; 6 JVM tests passed.
- Full Android regression suite: **9/9 passed**, 103.877 seconds, on the Android
  15 x86_64 tablet emulator. This includes capture/pause/rollover/screen-off,
  import, native speech recognition, queue/retention, store, themes, transcripts
  and the new help-navigation test.
- After the final typography-only change, the signed release passed the help
  test again on the 800 x 1280 / 160 dpi tablet (**1/1**, 10.679 seconds) and
  1080 x 2400 / 420 dpi phone with font scale 1.3 (**1/1**, 11.896 seconds).
  Each run covers both languages and themes, reaching the final paragraph,
  activity recreation, the explicit back button and the Android Back key.
  Emulator display/font overrides were restored afterwards.
- Screenshots from the final signed release are under `artifacts/qa/help/`.
  Tablet and phone samples were visually checked for wrapping and readability.
- APK signature, existing certificate, payload/model digest and 16 KB zip/native
  alignment passed again. No INTERNET permission, private media or signing keys
  are included. No dependencies or remote help services were added.

Prepared APK:
`artifacts/private-release/scribe-0.1.1-test-vc2-d06e3745/scribe-0.1.1-test-vc2.apk`
(65,558,808 bytes).

SHA-256: `d06e374579569d2b7c80487fa6862e90163fbb51863ca2b0db58f2267975783a`.
The package includes installation instructions, a checksum and `release.json`.
Logs for this work use the `help-` prefix. The physical Samsung tablet and its
microphone, battery, heat and long-lecture performance remain unverified here.
