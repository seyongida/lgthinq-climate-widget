#!/usr/bin/env python3
"""Check the public Android package before any release upload (never prints PAT values)."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('apk', type=Path)
parser.add_argument('--version')
args = parser.parse_args()
metadata = json.loads((args.apk.parent / 'output-metadata.json').read_text())
assert metadata['variantName'] == 'release', 'Only a release variant may be published'
assert metadata['applicationId'] == 'dev.thinqclimate.widget.release', 'Unexpected public app ID'
version = metadata['elements'][0]['versionName']
if args.version:
    assert version == args.version.removeprefix('v'), 'Tag and APK versions differ'
config = Path('android/app/build/generated/source/buildConfig/release/dev/thinqclimate/widget/BuildConfig.java').read_text()
assert re.search(r'THINQ_PAT\s*=\s*"";', config), 'Public BuildConfig must have empty credentials'
assert re.search(r'DEBUG\s*=\s*false;', config), 'Public APK must not be debuggable'
with zipfile.ZipFile(args.apk) as archive:
    for name in archive.namelist():
        assert not name.endswith(('.env', '.jks', '.keystore', '.p12')), 'Private file packaged in release'
        if name.endswith('.dex'):
            assert not re.search(rb'thinqpat_[A-Za-z0-9]{12,}', archive.read(name)), 'Embedded PAT in public APK'
# Also check the exact local token if this developer checkout contains .env.
env = Path('.env')
if env.exists():
    token = next((line.split('=', 1)[1].strip().strip('\"\'') for line in env.read_text().splitlines() if line.startswith('THINQ_PAT=')), '')
    if token:
        with zipfile.ZipFile(args.apk) as archive:
            assert not any(token.encode() in archive.read(name) for name in archive.namelist()), 'Local PAT leaked into public APK'
print('Verified public APK:', metadata['applicationId'], version)
print('SHA-256:', hashlib.sha256(args.apk.read_bytes()).hexdigest())
