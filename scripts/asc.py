#!/usr/bin/env python3
"""App Store Connect helpers for the Ad Hoc fallback. Never print keys, passwords or UDIDs."""
import argparse
import base64
import json
import os
from pathlib import Path
import plistlib
import re
import secrets
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

OPENSSL = '/usr/bin/openssl' if Path('/usr/bin/openssl').exists() else 'openssl'
UDID = re.compile(r'[0-9a-fA-F]{40}|[0-9a-fA-F]{8}-[0-9a-fA-F]{16}')


def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b'=').decode()


def der_to_raw(der):
    """Convert an ECDSA DER signature to the fixed-width r||s form JWT requires."""
    if der[0] != 0x30 or der[2] != 0x02:
        raise ValueError('Unexpected ECDSA signature')
    r_len = der[3]
    r = der[4:4 + r_len]
    if der[4 + r_len] != 0x02:
        raise ValueError('Unexpected ECDSA signature')
    s = der[6 + r_len:6 + r_len + der[5 + r_len]]
    return r.lstrip(b'\0').rjust(32, b'\0') + s.lstrip(b'\0').rjust(32, b'\0')


def make_token(key_path, key_id, issuer, now=None):
    now = int(now or time.time())
    header = {'alg': 'ES256', 'kid': key_id, 'typ': 'JWT'}
    payload = {'iss': issuer, 'iat': now, 'exp': now + 1200, 'aud': 'appstoreconnect-v1'}
    signing_input = b64url(json.dumps(header).encode()) + '.' + b64url(json.dumps(payload).encode())
    der = subprocess.run([OPENSSL, 'dgst', '-sha256', '-sign', str(key_path)], input=signing_input.encode(),
                         stdout=subprocess.PIPE, check=True).stdout
    return signing_input + '.' + b64url(der_to_raw(der))


class Client:
    def __init__(self, key_path, key_id, issuer):
        self.key_path, self.key_id, self.issuer = key_path, key_id, issuer
        self.base = os.environ.get('ASC_API_BASE', 'https://api.appstoreconnect.apple.com')

    def request(self, method, path, params=None, body=None):
        url = self.base + path + ('?' + urllib.parse.urlencode(params) if params else '')
        request = urllib.request.Request(url, method=method, data=json.dumps(body).encode() if body else None)
        request.add_header('Authorization', 'Bearer ' + make_token(self.key_path, self.key_id, self.issuer))
        request.add_header('Content-Type', 'application/json')
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                data = response.read()
        except urllib.error.HTTPError as error:
            try:
                titles = [e.get('title', '') for e in json.loads(error.read()).get('errors', [])]
            except ValueError:
                titles = []
            raise SystemExit(f'App Store Connect {method} {path} failed: HTTP {error.code} {"; ".join(titles)}')
        return json.loads(data) if data else {}

    def get(self, path, **params):
        query = {f'filter[{key[7:]}]' if key.startswith('filter_') else key: value for key, value in params.items()}
        return self.request('GET', path, query)['data']


def client_from_env():
    """Write the base64 key from the environment to a private temp file."""
    for name in ['ASC_API_KEY_P8_BASE64', 'ASC_API_KEY_ID', 'ASC_API_ISSUER_ID']:
        if not os.environ.get(name):
            raise SystemExit('Missing ' + name)
    handle = tempfile.NamedTemporaryFile(prefix='asc-', suffix='.p8', delete=False)
    handle.write(base64.b64decode(os.environ['ASC_API_KEY_P8_BASE64'], validate=True))
    handle.close()
    os.chmod(handle.name, 0o600)
    import atexit
    atexit.register(lambda: Path(handle.name).unlink(missing_ok=True))
    return Client(handle.name, os.environ['ASC_API_KEY_ID'], os.environ['ASC_API_ISSUER_ID'])


def review_state(client, bundle_id):
    """Return the beta review state of the newest submitted build, or NONE."""
    apps = client.get('/v1/apps', filter_bundleId=bundle_id, limit=1)
    if not apps:
        raise SystemExit('No App Store Connect app for ' + bundle_id)
    builds = client.get('/v1/builds', filter_app=apps[0]['id'], sort='-uploadedDate', limit=10)
    for build in builds:
        submissions = client.get('/v1/betaAppReviewSubmissions', filter_build=build['id'], limit=1)
        if submissions:
            return submissions[0]['attributes']['betaReviewState']
    return 'NONE'


def parse_devices(text):
    """Parse one 'name|udid' per line."""
    devices = []
    for line in filter(None, (raw.strip() for raw in text.splitlines())):
        name, separator, udid = line.rpartition('|')
        if not separator or not name.strip() or not UDID.fullmatch(udid.strip()):
            raise SystemExit('Each device must be "name|UDID"')
        devices.append((name.strip(), udid.strip()))
    if not devices:
        raise SystemExit('No devices configured')
    return devices


def parse_export(text):
    """Reads Apple's bulk device format, which Firebase App Distribution exports: Device ID, Device Name, Device Platform."""
    devices = []
    for line in text.splitlines():
        parts = [p.strip() for p in line.split('\t')]
        if len(parts) < 2 or parts[0].lower() in ('device id', 'deviceid') or not UDID.fullmatch(parts[0]):
            continue
        if len(parts) > 2 and parts[2] and parts[2].lower() not in ('ios', 'ipados'):
            continue
        name = parts[1].replace('|', ' ') or 'iPhone'
        if all(udid != parts[0] for _, udid in devices):
            devices.append((name, parts[0]))
    if not devices:
        raise SystemExit('No iPhone or iPad UDIDs found in the file')
    return devices


def apple_devices(client):
    """iPhones and iPads already registered in the Apple Developer account, as (name, UDID)."""
    result = []
    for item in client.get('/v1/devices', filter_platform='IOS', filter_status='ENABLED', limit=200):
        a = item['attributes']
        if a.get('deviceClass') in ('IPHONE', 'IPAD', 'IPOD') and UDID.fullmatch(a.get('udid', '')):
            result.append(((a.get('name') or 'iPhone').replace('|', ' '), a['udid']))
    return result


def merge_devices(*lists):
    seen, result = set(), []
    for devices in lists:
        for name, udid in devices:
            if udid.lower() not in seen:
                seen.add(udid.lower()); result.append((name, udid))
    return result


def device_id(client, name, udid):
    found = client.get('/v1/devices', filter_udid=udid, limit=1)
    if found:
        if found[0]['attributes'].get('status') == 'DISABLED':
            raise SystemExit('A configured device is disabled in the Apple Developer account')
        return found[0]['id']
    body = {'data': {'type': 'devices', 'attributes': {'name': name, 'platform': 'IOS', 'udid': udid}}}
    return client.request('POST', '/v1/devices', body=body)['data']['id']


def bundle_resource_id(client, identifier):
    # filter[identifier] also matches longer identifiers, so compare exactly.
    for item in client.get('/v1/bundleIds', filter_identifier=identifier, limit=200):
        if item['attributes']['identifier'] == identifier:
            return item['id']
    raise SystemExit('Bundle ID is not registered: ' + identifier)


def create_profile(client, name, bundle_id, certificate_id, device_ids):
    # A profile's device list is fixed, so replace it instead of reusing it.
    for old in client.get('/v1/profiles', filter_name=name, limit=200):
        if old['attributes']['name'] == name:
            client.request('DELETE', '/v1/profiles/' + old['id'])
    body = {'data': {'type': 'profiles',
                     'attributes': {'name': name, 'profileType': 'IOS_APP_ADHOC'},
                     'relationships': {
                         'bundleId': {'data': {'type': 'bundleIds', 'id': bundle_id}},
                         'certificates': {'data': [{'type': 'certificates', 'id': certificate_id}]},
                         'devices': {'data': [{'type': 'devices', 'id': item} for item in device_ids]}}}}
    return client.request('POST', '/v1/profiles', body=body)['data']['attributes']


def prepare_adhoc(client, signing_dir, team, bundle, certificate_id, devices_text):
    """Register devices, create both profiles limited to those devices, and write export options."""
    device_ids = [device_id(client, name, udid) for name, udid in parse_devices(devices_text)]
    signing_dir = Path(signing_dir)
    signing_dir.mkdir(parents=True, exist_ok=True)
    directories = [Path.home() / 'Library/MobileDevice/Provisioning Profiles',
                   Path.home() / 'Library/Developer/Xcode/UserData/Provisioning Profiles']
    profiles = {}
    for role, identifier in [('app', bundle), ('widget', bundle + '.extension')]:
        attributes = create_profile(client, f'ThinQ AdHoc {role}', bundle_resource_id(client, identifier),
                                    certificate_id, device_ids)
        paths = []
        for directory in directories:
            directory.mkdir(parents=True, exist_ok=True)
            path = directory / (attributes['uuid'] + '.mobileprovision')
            path.write_bytes(base64.b64decode(attributes['profileContent']))
            paths.append(str(path))
        profiles[role] = {'name': attributes['name'], 'paths': paths}
    (signing_dir / 'profiles.json').write_text(json.dumps(profiles))
    export = {'method': 'release-testing', 'destination': 'export', 'signingStyle': 'manual', 'teamID': team,
              'thinning': '<none>', 'provisioningProfiles': {bundle: profiles['app']['name'],
                                                            bundle + '.extension': profiles['widget']['name']}}
    (signing_dir / 'ExportOptions.plist').write_bytes(plistlib.dumps(export))
    return len(device_ids)


def google_token(key):
    """OAuth token for a Google service account key, signed with the system openssl."""
    now = int(time.time())
    claims = {'iss': key['client_email'], 'scope': 'https://www.googleapis.com/auth/cloud-platform',
              'aud': key['token_uri'], 'iat': now, 'exp': now + 600}
    signing_input = b64url(json.dumps({'alg': 'RS256', 'typ': 'JWT'}).encode()) + '.' + b64url(json.dumps(claims).encode())
    with tempfile.NamedTemporaryFile('w', delete=False) as handle:
        handle.write(key['private_key'])
    try:
        os.chmod(handle.name, 0o600)
        signature = subprocess.run([OPENSSL, 'dgst', '-sha256', '-sign', handle.name], input=signing_input.encode(),
                                   stdout=subprocess.PIPE, check=True).stdout
    finally:
        Path(handle.name).unlink(missing_ok=True)
    body = urllib.parse.urlencode({'grant_type': 'urn:ietf:params:oauth:grant-type:jwt-bearer',
                                   'assertion': signing_input + '.' + b64url(signature)}).encode()
    with urllib.request.urlopen(key['token_uri'], body, timeout=30) as response:
        return json.loads(response.read())['access_token']


def check_firebase(key, app_id, groups):
    """Confirms the key can reach the app's Firebase project and the tester groups exist."""
    number = app_id.split(':')[1]
    base = os.environ.get('FIREBASE_API_BASE', 'https://firebaseappdistribution.googleapis.com')
    request = urllib.request.Request(f'{base}/v1/projects/{number}/groups', headers={'Authorization': 'Bearer ' + google_token(key)})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            found = {g['name'].split('/')[-1] for g in json.loads(response.read()).get('groups', [])}
    except urllib.error.HTTPError as error:
        message = json.loads(error.read() or b'{}').get('error', {}).get('message', '')
        other = re.search(r'in project (\d+)', message)
        if other and other.group(1) != number:
            raise SystemExit(f'This key belongs to Google Cloud project {other.group(1)}, but the app is in project {number}. '
                             'Create the service account from Firebase project settings → Service accounts of the app\'s project.')
        raise SystemExit(f'Firebase rejected the key (HTTP {error.code}). Give the service account the Firebase App Distribution Admin role.')
    missing = [g for g in groups.split(',') if g and g not in found]
    if missing:
        raise SystemExit(f'Tester group alias not found: {", ".join(missing)}. Existing: {", ".join(sorted(found)) or "none"}')


def gh_secret(name, value):
    subprocess.run(['gh', 'secret', 'set', name], input=value.encode(), stdout=subprocess.DEVNULL, check=True)


def create_certificate(client):
    """Create an Apple Distribution certificate and return (id, p12 bytes, password)."""
    password = secrets.token_hex(24)
    with tempfile.TemporaryDirectory() as directory:
        os.chmod(directory, 0o700)
        key, csr, cert, p12 = (Path(directory) / name for name in ['key.pem', 'csr.pem', 'cert.pem', 'dist.p12'])
        subprocess.run([OPENSSL, 'req', '-new', '-newkey', 'rsa:2048', '-nodes', '-keyout', str(key), '-out', str(csr),
                        '-subj', '/CN=ThinQ Climate Distribution'], check=True, capture_output=True)
        body = {'data': {'type': 'certificates', 'attributes': {'certificateType': 'DISTRIBUTION',
                                                                'csrContent': csr.read_text()}}}
        created = client.request('POST', '/v1/certificates', body=body)['data']
        subprocess.run([OPENSSL, 'x509', '-inform', 'der', '-out', str(cert)], check=True, capture_output=True,
                       input=base64.b64decode(created['attributes']['certificateContent']))
        subprocess.run([OPENSSL, 'pkcs12', '-export', '-inkey', str(key), '-in', str(cert), '-out', str(p12),
                        '-passout', 'env:THINQ_P12_PASSWORD'], check=True, capture_output=True,
                       env={**os.environ, 'THINQ_P12_PASSWORD': password})
        return created['id'], p12.read_bytes(), password


def setup(args):
    key = Path(args.key).expanduser()
    client = Client(str(key), args.key_id, args.issuer)
    bundle = args.bundle_id
    print('Checking the API key: review state', review_state(client, bundle))
    gh_secret('ASC_API_KEY_P8_BASE64', base64.b64encode(key.read_bytes()).decode())
    gh_secret('ASC_API_KEY_ID', args.key_id)
    gh_secret('ASC_API_ISSUER_ID', args.issuer)
    existing = subprocess.run(['gh', 'secret', 'list', '--json', 'name', '--jq', '.[].name'],
                              check=True, capture_output=True, text=True).stdout.split()
    if 'IOS_DISTRIBUTION_CERT_ID' in existing:
        print('Keeping the existing distribution certificate secrets')
    else:
        certificate_id, p12, password = create_certificate(client)
        gh_secret('IOS_DISTRIBUTION_P12_BASE64', base64.b64encode(p12).decode())
        gh_secret('IOS_DISTRIBUTION_P12_PASSWORD', password)
        gh_secret('IOS_DISTRIBUTION_CERT_ID', certificate_id)
        print('Created a distribution certificate and saved it to GitHub Secrets')
    if args.device:
        text = '\n'.join(args.device)
        print(f'Saved {len(parse_devices(text))} Ad Hoc device(s)')
        gh_secret('IOS_ADHOC_DEVICES', text)
    print('Setup complete')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('review-state')
    commands.add_parser('prepare-adhoc')
    configure = commands.add_parser('setup')
    configure.add_argument('--key', required=True, help='Path to AuthKey_XXXX.p8')
    configure.add_argument('--key-id', required=True)
    configure.add_argument('--issuer', required=True)
    configure.add_argument('--bundle-id', default='dev.thinqclimate.widget')
    configure.add_argument('--device', action='append', help='"name|UDID"; replaces the saved list')
    firebase = commands.add_parser('firebase', help='Save the Firebase service account key and app ID for distribution')
    firebase.add_argument('--key', required=True, help='Path to the service account JSON key')
    firebase.add_argument('--app-id', required=True, help='Firebase iOS app ID, like 1:1234567890:ios:abcdef')
    firebase.add_argument('--groups', default='friends', help='Comma-separated tester group aliases')
    imported = commands.add_parser('import-devices', help='Save the devices for Firebase builds and start a build')
    imported.add_argument('file', nargs='?', help='Firebase "Export Apple UDIDs" file')
    imported.add_argument('--apple', action='store_true', help='Also include every iPhone and iPad registered in the Apple account')
    imported.add_argument('--key', help='App Store Connect .p8 key, needed with --apple')
    imported.add_argument('--key-id')
    imported.add_argument('--issuer')
    imported.add_argument('--only', action='append', help='With --apple, keep only devices whose name contains this text (repeatable)')
    imported.add_argument('--dry-run', action='store_true', help='Only list the devices that would be saved')
    imported.add_argument('--no-build', action='store_true')
    args = parser.parse_args()

    if args.command == 'review-state':
        print(review_state(client_from_env(), os.environ.get('IOS_BUNDLE_ID', 'dev.thinqclimate.widget')))
    elif args.command == 'prepare-adhoc':
        count = prepare_adhoc(client_from_env(), os.environ['IOS_SIGNING_DIR'], os.environ['IOS_TEAM_ID'],
                              os.environ['IOS_BUNDLE_ID'], os.environ['IOS_DISTRIBUTION_CERT_ID'],
                              os.environ['IOS_ADHOC_DEVICES'])
        print(f'Prepared Ad Hoc profiles for {count} device(s)')
    elif args.command == 'firebase':
        if not re.fullmatch(r'1:\d+:ios:[0-9a-f]+', args.app_id):
            raise SystemExit('The app ID should look like 1:1234567890:ios:abcdef (Project settings → Your apps)')
        key = json.loads(Path(args.key).expanduser().read_text())
        if key.get('type') != 'service_account' or 'private_key' not in key:
            raise SystemExit('This is not a service account key file')
        check_firebase(key, args.app_id, args.groups)
        gh_secret('FIREBASE_SERVICE_ACCOUNT_BASE64', base64.b64encode(Path(args.key).expanduser().read_bytes()).decode())
        subprocess.run(['gh', 'variable', 'set', 'FIREBASE_IOS_APP_ID', '--body', args.app_id], check=True, stdout=subprocess.DEVNULL)
        subprocess.run(['gh', 'variable', 'set', 'FIREBASE_GROUPS', '--body', args.groups], check=True, stdout=subprocess.DEVNULL)
        print(f'Saved Firebase settings for project {key.get("project_id", "?")}')
    elif args.command == 'import-devices':
        lists = []
        if args.file:
            lists.append(parse_export(Path(args.file).expanduser().read_text(encoding='utf-8-sig')))
        if args.apple:
            if not (args.key and args.key_id and args.issuer):
                raise SystemExit('--apple needs --key, --key-id and --issuer')
            registered = apple_devices(Client(str(Path(args.key).expanduser()), args.key_id, args.issuer))
            if args.only:
                registered = [(n, u) for n, u in registered if any(o in n for o in args.only)]
            lists.append(registered)
        devices = merge_devices(*lists)
        if not devices:
            raise SystemExit('Give a Firebase export file, --apple, or both')
        # Names only: UDIDs stay out of terminal output and logs.
        print(f'{len(devices)} device(s): ' + ', '.join(name for name, _ in devices))
        if args.dry_run:
            return
        gh_secret('IOS_ADHOC_DEVICES', '\n'.join(f'{name}|{udid}' for name, udid in devices))
        print(f'Saved {len(devices)} device(s) for Firebase builds')
        if not args.no_build:
            subprocess.run(['gh', 'workflow', 'run', 'ios-adhoc.yml', '--ref', 'main'], check=True)
            print('Started a build; testers get it from Firebase when it finishes')
    else:
        setup(args)


if __name__ == '__main__':
    sys.exit(main())
