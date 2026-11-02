"""Audit the built APK payload, model digest and native page alignment."""
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MODEL = 'assets/models/ggml-base-q5_1.bin'
EXPECTED = '422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898'

def main():
    apk = Path(sys.argv[1]) if len(sys.argv)>1 else ROOT/'app/build/outputs/apk/release/app-release.apk'
    with zipfile.ZipFile(apk) as zipped:
        names = zipped.namelist()
        assert not any(n.lower().endswith(('.wav','.mp3','.m4a','.flac','.ogg','.p12','.jks','.keystore')) for n in names), 'Unexpected private/media/key payload'
        assets = set(n for n in names if n.startswith('assets/'))
        expected_assets = {MODEL,'assets/legal/whisper-MIT.txt','assets/legal/whisper-model-MIT.txt','assets/legal/scribe-MIT.txt'}
        lecture = None
        if 'assets/lecture-model.json' in names:
            lecture = json.loads(zipped.read('assets/lecture-model.json'))
            assert lecture == json.loads((ROOT/'app/src/main/assets/lecture-model.json').read_text('utf-8')), 'Lecture manifest mismatch'
            expected_assets |= {'assets/lecture-model.json', 'assets/legal/lecture-NOTICE.txt',
                'assets/legal/sherpa-onnx-Apache-2.0.txt', 'assets/legal/onnxruntime-MIT.txt',
                'assets/legal/onnxruntime-ThirdPartyNotices.txt'}
            for item in lecture['files']:
                asset = 'assets/models/parakeet-v3-int8/' + item['file']
                expected_assets.add(asset)
                assert zipped.getinfo(asset).file_size == item['bytes'], 'Lecture model size mismatch'
                with zipped.open(asset) as stream:
                    assert hashlib.file_digest(stream,'sha256').hexdigest() == item['sha256'], 'Lecture model digest mismatch'
        if 'META-INF/androidx.core_core.version' in names:
            expected_assets.add('assets/legal/AndroidX-Apache-2.0.txt')
        profiles = {'assets/dexopt/baseline.prof', 'assets/dexopt/baseline.profm'}
        assert assets in (expected_assets, expected_assets | profiles), 'Unexpected assets'
        # AndroidX supplies a compiled baseline profile, not application/user data.
        if profiles <= assets:
            for profile, magic in (('assets/dexopt/baseline.prof', b'pro\0'), ('assets/dexopt/baseline.profm', b'prm\0')):
                assert zipped.getinfo(profile).file_size < 1024 * 1024, 'Unexpected profile size'
                assert zipped.read(profile).startswith(magic), 'Invalid baseline profile'
        with zipped.open(MODEL) as stream:
            model_hash = hashlib.file_digest(stream,'sha256').hexdigest()
        assert model_hash == EXPECTED, 'Model digest mismatch'
        libraries = {}
        runtime = json.loads((ROOT/'tools/lecture-runtime.json').read_text('utf-8')) if lecture else None
        for abi in ('arm64-v8a','x86_64'):
          allowed_libraries = {'libscribe.so'} | (set(runtime['libraries'][abi]) if runtime else set())
          assert {n.rsplit('/',1)[1] for n in names if n.startswith(f'lib/{abi}/')} == allowed_libraries, 'Unexpected native library'
          for library in sorted(allowed_libraries):
            image = zipped.read(f'lib/{abi}/{library}')
            if library != 'libscribe.so':
                assert hashlib.sha256(image).hexdigest() == runtime['libraries'][abi][library], 'Runtime digest mismatch'
            assert image[:6] == b'\x7fELF\x02\x01', 'Expected 64-bit little-endian ELF'
            offset = struct.unpack_from('<Q',image,32)[0]
            size,count = struct.unpack_from('<HH',image,54)
            alignments=[]
            for i in range(count):
                ph=struct.unpack_from('<IIQQQQQQ',image,offset+i*size)
                if ph[0]==1:
                    assert ph[7]>=16384 and ph[2]%16384==ph[3]%16384, 'Native LOAD segment is not 16 KB compatible'
                    alignments.append(ph[7])
            assert alignments
            libraries[f'{abi}/{library}']={'bytes':len(image),'load_alignments':alignments}
    with apk.open('rb') as stream:
        digest=hashlib.file_digest(stream,'sha256').hexdigest()
    result={'apk':str(apk.resolve()),'bytes':apk.stat().st_size,'sha256':digest,'model_sha256':model_hash,'native':libraries,'assets_allowlist_passed':True,'private_media_and_keys_absent':True}
    output=ROOT/'artifacts/apk-audit.json'
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result,indent=2))

if __name__=='__main__':
    main()
