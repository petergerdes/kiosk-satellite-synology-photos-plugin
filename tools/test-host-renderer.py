#!/usr/bin/env python3
"""Generate a browser regression check from a patched Kiosk Satellite checkout."""
import argparse
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('checkout', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
source = (args.checkout / 'app/lib/ui/plugin_screensaver.dart').read_text()
start = source.index('String pluginScreensaverDocument(')
end = source.index('/// Configuration travels', start)
output = root / '.cache/host-renderer-check'
output.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(prefix='host-renderer-check-') as folder:
    runner = Path(folder) / 'render.dart'
    runner.write_text("import 'dart:convert';\n" + source[start:end] + "\nvoid main() { print(pluginScreensaverDocument('<body style=\"background:#900\">Current photo</body>')); }\n")
    result = subprocess.run(['dart', str(runner)], check=True, capture_output=True, text=True)
    (output / 'host.html').write_text(result.stdout)
(output / 'index.html').write_bytes((root / 'tests/host-renderer-check.html').read_bytes())
print('Generated .cache/host-renderer-check from the actual host document builder.')
print('Serve with: python3 -m http.server 8765 --bind 127.0.0.1 --directory .cache/host-renderer-check')
