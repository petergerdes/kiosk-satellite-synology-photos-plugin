#!/usr/bin/env python3
"""Check a private shared album using real Android codecs on a connected developer emulator."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

from android_sdk import android_platform

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('config', type=Path, help='Private JSON with albumUrl and albumPassword')
parser.add_argument('--serial', required=True, help='Developer emulator serial from adb devices')
args = parser.parse_args()
sdk = Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', str(Path.home() / 'android-sdk'))))
java_home = os.environ.get('JAVA_HOME')
def java(name):
    return str(Path(java_home) / 'bin' / name) if java_home else name

adb = [str(sdk / 'platform-tools/adb'), '-s', args.serial]
tools = sorted((sdk / 'build-tools').glob('*/d8'), key=lambda p: tuple(int(v) for v in p.parent.name.split('.') if v.isdigit()))
if not tools:
    raise SystemExit('Install Android build-tools and set ANDROID_HOME.')
platform = android_platform(sdk)
# Use a unique device directory so this check does not overwrite other test data.
with tempfile.TemporaryDirectory(prefix='synology-android-check-') as folder:
    temp = Path(folder)
    classes = temp / 'classes'
    dex = temp / 'dex'
    classes.mkdir()
    dex.mkdir()
    sources = [*sorted((root / 'src').rglob('*.java')), *sorted((root / 'sdk/src').rglob('*.java')),
               *sorted((root / 'tests/native').rglob('*.java'))]
    subprocess.run([java('javac'), '--release', '8', '-cp', str(platform), '-d', str(classes), *map(str, sources)], check=True)
    subprocess.run([str(tools[-1]), '--min-api', '24', '--lib', str(platform), '--output', str(dex),
                    *map(str, sorted(classes.rglob('*.class')))], check=True)
    device_dir = '/data/local/tmp/' + temp.name
    subprocess.run([*adb, 'shell', 'mkdir', '-m', '700', device_dir], check=True)
    try:
        subprocess.run([*adb, 'push', str(dex / 'classes.dex'), device_dir + '/check.dex'], check=True)
        subprocess.run([*adb, 'push', str(args.config.resolve()), device_dir + '/config.json'], check=True)
        subprocess.run([*adb, 'shell', 'CLASSPATH=' + device_dir + '/check.dex', 'app_process', '/system/bin',
                        'io.github.petergerdes.kiosk.synology.AndroidLiveCheck', device_dir + '/config.json',
                        device_dir + '/preview.html'], check=True, timeout=180)
        preview = root / '.cache/preview'
        preview.mkdir(parents=True, exist_ok=True)
        subprocess.run([*adb, 'pull', device_dir + '/preview.html', str(preview / 'android.html')], check=True)
    finally:
        subprocess.run([*adb, 'shell', 'rm', '-rf', device_dir], check=True)
