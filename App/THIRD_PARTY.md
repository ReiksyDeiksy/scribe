# Third-party components

## whisper.cpp and ggml

- Repository: https://github.com/ggml-org/whisper.cpp
- Version: v1.8.2
- Pinned commit: `4979e04f5dcaccb36057e059bbaed8a2f5288315`
- Licence: MIT; retained with the source and in APK assets under `legal/`.
- Compiled for Android arm64-v8a and x86_64, CPU only, no OpenMP or dynamic backend loading.

## Compatibility speech model

- Repository: https://huggingface.co/ggerganov/whisper.cpp
- Revision: `5359861c739e955e79d9a303bcbc70fb988958b1`
- File: `ggml-base-q5_1.bin`, multilingual, 59,707,625 bytes.
- SHA-256: `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898`
- The underlying Whisper code and weights are MIT licensed:
  https://github.com/openai/whisper/blob/main/LICENSE
- Weights are pre-trained. No user data is included and no training happens in the app.

## Lecture speech model

- Original: https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3
- NVIDIA Parakeet TDT 0.6B v3, CC BY 4.0:
  https://creativecommons.org/licenses/by/4.0/
- INT8 ONNX conversion by Fangjun Kuang / sherpa-onnx:
  https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8
- Pinned revision: `2bda32ec70b097a55adaa07d9a7173915b43cc78`.
- Four files total 670,478,772 bytes. Sizes and SHA-256 digests are recorded in
  `app/src/main/assets/lecture-model.json` and checked during preparation and APK
  auditing. Model weights remain excluded from Git.
- Attribution, conversion details and the licence link are packaged in
  `app/src/main/assets/legal/lecture-NOTICE.txt`. No user audio is included.

## Lecture runtime

- sherpa-onnx 2.0.1, Apache 2.0:
  https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1
- ONNX Runtime 1.28.2, MIT; the runtime's third-party notices are also included.
- Prebuilt Android arm64-v8a and x86_64 libraries use the CPU backend. The public
  archive, C header and individual runtime libraries are hash-pinned by
  `tools/prepare_lecture.py` and `tools/lecture-runtime.json`.
- Licences/notices are packaged under `assets/legal/` as
  `sherpa-onnx-Apache-2.0.txt`, `onnxruntime-MIT.txt` and
  `onnxruntime-ThirdPartyNotices.txt`.

## Developer tests and tools

JUnit 4.13.2 and AndroidX test runner 1.7.0 / JUnit integration 1.3.0 are used only
by developer tests. They are not app analytics or reporting SDKs. Public `jfk.wav`
comes from whisper.cpp's reference samples and is packaged only in the test APK.
Google Java Format 1.24.0 is an optional formatting tool, outside the app.

Build tools and download archives are kept in `.tools` and excluded from Git and
APKs. Verified speech models and the required Lecture runtime libraries are
bundled in the app APK; private test inputs and signing material are excluded.
