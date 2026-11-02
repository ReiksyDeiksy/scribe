# scribe

Local audio recording and transcription for Windows and Android. Both applications
are maintained in this repository, with separate source trees and build tools.

| Project | Source and instructions | Technology |
| --- | --- | --- |
| Windows desktop | [PC/README.md](PC/README.md) | Python, PySide6, faster-whisper |
| Android lecture recorder | [App/README.md](App/README.md) | Java, Android SDK, sherpa-onnx / whisper.cpp |

## Windows desktop

The desktop source, tests, packaging scripts and launcher are in `PC/`.
Start the application with `PC/start.bat`. Run desktop development and packaging
commands from that directory; see [the desktop instructions](PC/README.md).

## Android

The independent Android application is in `App/`, including English and Romanian
interfaces, recording, timestamp marks and local transcription. See
[the Android instructions](App/README.md) for toolchain preparation and builds, and
[the validation notes](App/VALIDATION.md) for prior checks and real-device limits.

Lecture mode uses Parakeet TDT through sherpa-onnx; Compatibility mode retains
Whisper. The pinned whisper.cpp source and third-party licences are included.
Public speech models, the prebuilt Lecture runtime and the build toolchain are
downloaded by the preparation scripts and excluded from Git. Signing keys remain
private in `App/.tools/signing/`.

## Working across computers

Clone or update the entire repository, then work in `PC/` or `App/` as needed.
Commit and push changes before changing computers; pull the latest changes before
starting work on the other computer. The desktop files that previously lived at
the repository root now live under `PC/`.

Recordings, transcripts, local settings, environments, build outputs and signing
keys are excluded from version control. Each application has additional ignore
rules in its own directory.

## Licence

See [LICENSE](LICENSE). Third-party Android components and model attribution are
documented in [App/THIRD_PARTY.md](App/THIRD_PARTY.md).
