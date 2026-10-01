#!/usr/bin/env python3
"""Run NAS protocol and plugin lifecycle checks without a NAS or Android device."""
import hashlib
import argparse
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import urllib.request

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--live-config', type=Path, help='Private JSON file with albumUrl and albumPassword for a live NAS check')
parser.add_argument('--preview', action='store_true', help='Generate synthetic transition previews in .cache/preview')
args = parser.parse_args()
# Android provides org.json at runtime. This pinned jar is for JVM tests only.
json_jar = root / '.cache/json-20240303.jar'
digest = '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'
if not json_jar.exists():
    json_jar.parent.mkdir(exist_ok=True)
    with urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar', timeout=30) as response:
        json_jar.write_bytes(response.read())
if hashlib.sha256(json_jar.read_bytes()).hexdigest() != digest:
    raise SystemExit('Test JSON jar checksum mismatch. Remove .cache/json-20240303.jar and retry.')

java_home = os.environ.get('JAVA_HOME')
def tool(name):
    return str(Path(java_home) / 'bin' / name) if java_home else name

sources = [*sorted((root / 'sdk/src').rglob('*.java')),
           *sorted((root / 'src').rglob('*.java')),
           *sorted((root / 'tests').rglob('*.java'))]
with tempfile.TemporaryDirectory(prefix='synology-plugin-test-') as directory:
    subprocess.run([tool('javac'), '--release', '8', '-cp', str(json_jar), '-d', directory, *map(str, sources)], check=True)
    subprocess.run([tool('java'), '-ea', '-cp', os.pathsep.join([directory, str(json_jar)]),
                    'io.github.petergerdes.kiosk.synology.PluginTest',
                    *([str(args.live_config.resolve())] if args.live_config else [])], check=True)
    if args.preview:
        subprocess.run([tool('java'), '-cp', os.pathsep.join([directory, str(json_jar)]),
                        'io.github.petergerdes.kiosk.synology.PluginTest', '--preview', str(root / '.cache/preview')], check=True)

for check in ['test_android_sdk.py', 'test_plugin_manifest.py', 'test_plugin_assets.py']:
    subprocess.run([sys.executable, str(root / 'tools' / check)], check=True)
