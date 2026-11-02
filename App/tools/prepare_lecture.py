"""Prepare the pinned offline lecture model and Android runtime. Never accesses user audio."""
import json
from pathlib import Path
import shutil
import tarfile
from bootstrap import ROOT, TOOLS, fetch

VERSION = '2.0.1'
RUNTIME_SHA256 = '39003753ec293425ed56723ea9b972b98841a490af3311670c3060548d654330'
REPOSITORY = 'https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8'


def main():
    metadata = json.loads((ROOT / 'app/src/main/assets/lecture-model.json').read_text('utf-8'))
    for item in metadata['files']:
        name = item['file']
        if Path(name).name != name or name not in ('encoder.int8.onnx', 'decoder.int8.onnx', 'joiner.int8.onnx', 'tokens.txt'):
            raise ValueError('Invalid model manifest')
        dest = ROOT / 'app/src/main/assets/models/parakeet-v3-int8' / name
        fetch(f"{REPOSITORY}/resolve/{metadata['revision']}/{name}", dest, item['sha256'])
        if dest.stat().st_size != item['bytes']:
            raise ValueError('Unexpected model size')
    archive = fetch(f'https://github.com/k2-fsa/sherpa-onnx/releases/download/v{VERSION}/sherpa-onnx-v{VERSION}-android.tar.bz2',
                    TOOLS / f'sherpa-onnx-v{VERSION}-android.tar.bz2', RUNTIME_SHA256)
    # Copy only regular, explicitly named library entries; never extract archive paths.
    with tarfile.open(archive) as source:
        for abi in ('arm64-v8a', 'x86_64'):
            for name in ('libsherpa-onnx-c-api.so', 'libonnxruntime.so'):
                entry = source.getmember(f'./jniLibs/{abi}/{name}')
                if not entry.isfile() or entry.size > 64 * 1024 * 1024:
                    raise ValueError('Invalid native runtime entry')
                dest = TOOLS / 'lecture-runtime/jniLibs' / abi / name
                dest.parent.mkdir(parents=True, exist_ok=True)
                temporary = dest.with_suffix('.partial')
                with source.extractfile(entry) as inp, temporary.open('wb') as out:
                    shutil.copyfileobj(inp, out)
                temporary.replace(dest)
    fetch(f'https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/v{VERSION}/sherpa-onnx/c-api/c-api.h',
          TOOLS / 'lecture-runtime/include/sherpa-c-api.h', HEADER_SHA256)
    print('Pinned lecture assets ready; no recordings or signing material included.')


# Pinned to the same release as the prebuilt runtime, including the full ABI layout.
HEADER_SHA256 = '5698f38ba09ad2bdf085c56a6cbbd7741acf15e25f4ce5c7097b9702cadf437f'

if __name__ == '__main__':
    main()
