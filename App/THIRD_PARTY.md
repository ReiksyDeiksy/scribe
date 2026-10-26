# Third-party components

## whisper.cpp and ggml

- Repository: https://github.com/ggml-org/whisper.cpp
- Version: v1.8.2
- Pinned commit: `4979e04f5dcaccb36057e059bbaed8a2f5288315`
- Licence: MIT; retained with the source and in APK assets under `legal/`.
- Compiled for Android arm64-v8a and x86_64, CPU only, no OpenMP or dynamic backend loading.

## Speech model

- Repository: https://huggingface.co/ggerganov/whisper.cpp
- Revision: `5359861c739e955e79d9a303bcbc70fb988958b1`
- File: `ggml-base-q5_1.bin`, multilingual, 59,707,625 bytes.
- SHA-256: `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898`
- The underlying Whisper code and weights are MIT licensed:
  https://github.com/openai/whisper/blob/main/LICENSE
- Weights are pre-trained. No user data is included and no training happens in the app.

## Developer tests and tools

JUnit 4.13.2 and AndroidX test runner 1.7.0 / JUnit integration 1.3.0 are used only
by developer tests. They are not app analytics or reporting SDKs. Public `jfk.wav`
comes from whisper.cpp's reference samples and is packaged only in the test APK.
Google Java Format 1.24.0 is an optional formatting tool, outside the app.

Build/runtime downloads are kept in `.tools` and excluded from Git and APKs.
