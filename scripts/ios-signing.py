#!/usr/bin/env python3
"""Prepare temporary Apple signing material. Never print secret values or device UDIDs."""
import base64
import json
import os
from pathlib import Path
import plistlib
import re
import subprocess
import sys

required = ['IOS_TEAM_ID', 'IOS_BUNDLE_ID', 'IOS_APP_GROUP', 'IOS_BUILD_NUMBER',
            'IOS_DISTRIBUTION_P12_BASE64', 'IOS_APP_PROFILE_BASE64', 'IOS_WIDGET_PROFILE_BASE64',
            'ASC_API_KEY_P8_BASE64', 'ASC_API_KEY_ID', 'ASC_API_ISSUER_ID']
missing = [name for name in required if not os.environ.get(name)]
if missing:
    raise SystemExit('Configure these GitHub variables/secrets: ' + ', '.join(missing))
assert re.fullmatch(r'[1-9][0-9]{0,3}(\.[0-9]{1,2}){0,2}', os.environ['IOS_BUILD_NUMBER']), 'Invalid build number'
assert re.fullmatch(r'[A-Za-z0-9]+', os.environ['ASC_API_KEY_ID']), 'Invalid API key ID'
if '--check' in sys.argv:
    print('Apple distribution inputs are present')
    raise SystemExit(0)

target = Path(os.environ['IOS_SIGNING_DIR'])
target.mkdir(parents=True, exist_ok=True)
target.chmod(0o700)
for key, filename in [('IOS_DISTRIBUTION_P12_BASE64', 'distribution.p12'),
                      ('ASC_API_KEY_P8_BASE64', 'AuthKey.p8')]:
    file = target / filename
    file.write_bytes(base64.b64decode(os.environ[key], validate=True))
    file.chmod(0o600)
profiles = {}
for key, role, identifier in [('IOS_APP_PROFILE_BASE64', 'app', os.environ['IOS_BUNDLE_ID']),
                              ('IOS_WIDGET_PROFILE_BASE64', 'widget', os.environ['IOS_BUNDLE_ID'] + '.extension')]:
    data = base64.b64decode(os.environ[key], validate=True)
    raw = target / (role + '.mobileprovision')
    raw.write_bytes(data)
    profile = plistlib.loads(subprocess.check_output(['security', 'cms', '-D', '-i', str(raw)]))
    entitlements = profile['Entitlements']
    assert os.environ['IOS_TEAM_ID'] in profile['TeamIdentifier'], 'Profile team mismatch'
    assert entitlements['application-identifier'].endswith('.' + identifier), 'Profile app ID mismatch'
    assert not profile.get('ProvisionedDevices') and not profile.get('ProvisionsAllDevices'), 'Use App Store profiles, not Ad Hoc/enterprise profiles'
    assert not entitlements.get('get-task-allow', False), 'Use App Store distribution profiles, not development profiles'
    assert os.environ['IOS_APP_GROUP'] in entitlements.get('com.apple.security.application-groups', []), 'App Group is missing from profile'
    assert re.fullmatch(r'[A-Fa-f0-9-]+', profile['UUID']), 'Invalid profile UUID'
    directory = Path.home() / 'Library/MobileDevice/Provisioning Profiles'
    directory.mkdir(parents=True, exist_ok=True)
    installed = directory / (profile['UUID'] + '.mobileprovision')
    installed.write_bytes(data)
    profiles[role] = {'name': profile['Name'], 'path': str(installed)}
    (target / 'profiles.json').write_text(json.dumps(profiles))
(target / 'profiles.json').write_text(json.dumps(profiles))
export = {'method': 'app-store-connect', 'destination': 'upload', 'signingStyle': 'manual',
          'teamID': os.environ['IOS_TEAM_ID'], 'manageAppVersionAndBuildNumber': False,
          'uploadSymbols': True,
          'provisioningProfiles': {os.environ['IOS_BUNDLE_ID']: profiles['app']['name'],
                                   os.environ['IOS_BUNDLE_ID'] + '.extension': profiles['widget']['name']}}
(target / 'ExportOptions.plist').write_bytes(plistlib.dumps(export))
print('Temporary Apple signing material prepared')
