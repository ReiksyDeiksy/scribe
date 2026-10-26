"""Prepare the public model and a persistent local signing key, without copying PC data."""
import json
import os
from pathlib import Path
import secrets
import subprocess
from bootstrap import ROOT, TOOLS, JAVA, fetch

MODEL_REVISION = '5359861c739e955e79d9a303bcbc70fb988958b1'
MODEL_SHA256 = '422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898'

def main():
    fetch(f'https://huggingface.co/ggerganov/whisper.cpp/resolve/{MODEL_REVISION}/ggml-base-q5_1.bin',
          ROOT / 'app/src/main/assets/models/ggml-base-q5_1.bin', MODEL_SHA256)
    signing = TOOLS / 'signing'
    config = signing / 'config.json'
    if not config.exists():
        signing.mkdir(parents=True, exist_ok=True)
        keystore = signing / 'scribe.p12'
        if keystore.exists():
            raise RuntimeError('Existing keystore without its configuration: restore config.json; do not replace the key.')
        password = secrets.token_urlsafe(36)
        env = os.environ | {'SCRIBE_KEYSTORE_PASSWORD': password}
        subprocess.run([str(JAVA / 'bin/keytool.exe'), '-genkeypair', '-storetype', 'PKCS12',
            '-keystore', str(keystore), '-alias', 'scribe', '-keyalg', 'RSA', '-keysize', '3072',
            '-sigalg', 'SHA256withRSA', '-validity', '10000', '-dname', 'CN=scribe Android',
            '-storepass:env', 'SCRIBE_KEYSTORE_PASSWORD', '-keypass:env', 'SCRIBE_KEYSTORE_PASSWORD'],
            env=env, check=True)
        config.write_text(json.dumps({'storeFile': str(keystore), 'password': password, 'alias': 'scribe'}), encoding='utf-8')
        print('Persistent local signing key created. Keep .tools/signing private and backed up.')
    else:
        print('Existing signing key retained.')

if __name__ == '__main__':
    main()
