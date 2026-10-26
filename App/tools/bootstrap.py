"""Install a project-local, pinned Android build toolchain; never modifies PATH globally."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.request
import zipfile
import xml.etree.ElementTree as ET
import shutil

# Some Windows antivirus installations inject an inaccessible TLS key-log pipe.
# Downloads must keep TLS verification enabled, without exporting session keys.
os.environ.pop('SSLKEYLOGFILE', None)

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
SDK = TOOLS / 'android-sdk'
JAVA = TOOLS / 'jdk'

def fetch(url, dest, expected=None, algorithm='sha256'):
    dest = Path(dest)
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and (not expected or hashlib.new(algorithm, dest.read_bytes()).hexdigest() == expected):
        return dest
    print(f'Downloading {dest.name}', flush=True)
    req = urllib.request.Request(url, headers={'User-Agent': 'ScribeBuild/0.1'})
    temporary = dest.with_suffix(dest.suffix + '.partial')
    digest = hashlib.new(algorithm)
    with urllib.request.urlopen(req, timeout=120) as source, temporary.open('wb') as target:
        while block := source.read(1024 * 1024):
            target.write(block)
            digest.update(block)
    if expected and digest.hexdigest() != expected:
        raise RuntimeError(f'Checksum mismatch for {dest.name}')
    temporary.replace(dest)
    return dest

def main():
    TOOLS.mkdir(exist_ok=True)
    if not (JAVA / 'bin/jlink.exe').is_file():
        package = {'link': 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.zip',
                   'checksum': 'e53a79c3c3d86865bd7e787903884331068e71321714ffd44f145785affc7cb0'}
        jdk = fetch(package['link'], TOOLS / 'jdk17.zip', package['checksum'])
        with zipfile.ZipFile(jdk) as zipped:
            for entry in zipped.infolist():
                parts = Path(entry.filename).parts[1:]
                if not parts or entry.is_dir():
                    continue
                target = JAVA.joinpath(*parts).resolve()
                if not target.is_relative_to(JAVA.resolve()):
                    raise RuntimeError('Unsafe JDK archive path')
                target.parent.mkdir(parents=True, exist_ok=True)
                with zipped.open(entry) as source, target.open('wb') as out:
                    shutil.copyfileobj(source, out)
        (TOOLS / 'jdk-source.json').write_text(json.dumps(package, indent=2))
    metadata = urllib.request.urlopen('https://dl.google.com/android/repository/repository2-1.xml', timeout=120).read()
    tree = ET.fromstring(metadata)
    package = next(p for p in tree.findall('remotePackage') if p.attrib['path'] == 'cmdline-tools;19.0')
    archive = next(a for a in package.findall('archives/archive') if a.findtext('host-os') == 'windows')
    complete = archive.find('complete')
    url = complete.findtext('url')
    checksum = complete.find('checksum')
    command_zip = fetch('https://dl.google.com/android/repository/' + url, TOOLS / url, checksum.text, checksum.attrib.get('type', 'sha1'))
    cli = SDK / 'cmdline-tools/19.0'
    if not (cli / 'bin/sdkmanager.bat').exists():
        cli.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(command_zip) as zipped:
            for entry in zipped.infolist():
                name = entry.filename.removeprefix('cmdline-tools/')
                if not name or entry.is_dir():
                    continue
                target = (cli / name).resolve()
                if not target.is_relative_to(cli.resolve()):
                    raise RuntimeError('Unsafe archive path')
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(zipped.read(entry))
    env = os.environ | {'JAVA_HOME': str(JAVA), 'ANDROID_HOME': str(SDK), 'ANDROID_SDK_ROOT': str(SDK),
        'ANDROID_USER_HOME': str(TOOLS / 'android-user'), 'ANDROID_PREFS_ROOT': str(TOOLS / 'android-user'),
        'JAVA_TOOL_OPTIONS': '-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'}
    manager = str(cli / 'bin/sdkmanager.bat')
    subprocess.run([manager, '--sdk_root=' + str(SDK), '--licenses'], input='y\n' * 100, text=True, env=env, check=True)
    subprocess.run([manager, '--sdk_root=' + str(SDK), 'platform-tools', 'platforms;android-35', 'build-tools;35.0.0', 'ndk;27.0.12077973', 'cmake;3.22.1'], env=env, check=True)
    checksum = urllib.request.urlopen('https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256', timeout=60).read().decode().strip()
    gradle_zip = fetch('https://services.gradle.org/distributions/gradle-8.11.1-bin.zip', TOOLS / 'gradle-8.11.1-bin.zip', checksum)
    if not (TOOLS / 'gradle-8.11.1/bin/gradle.bat').exists():
        with zipfile.ZipFile(gradle_zip) as zipped:
            zipped.extractall(TOOLS)
    (ROOT / 'local.properties').write_text('sdk.dir=' + str(SDK).replace('\\', '/') + '\n', encoding='utf-8')
    print('Toolchain ready', flush=True)

if __name__ == '__main__':
    main()
