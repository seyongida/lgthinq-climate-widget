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


def manifest(ipa_url, bundle_id, version, title):
    return plistlib.dumps({'items': [{
        'assets': [{'kind': 'software-package', 'url': ipa_url}],
        'metadata': {'bundle-identifier': bundle_id, 'bundle-version': version, 'kind': 'software', 'title': title},
    }]})


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
    write = commands.add_parser('manifest')
    for option in ['--ipa-url', '--bundle-id', '--bundle-version', '--title', '--out']:
        write.add_argument(option, required=True)
    configure = commands.add_parser('setup')
    configure.add_argument('--key', required=True, help='Path to AuthKey_XXXX.p8')
    configure.add_argument('--key-id', required=True)
    configure.add_argument('--issuer', required=True)
    configure.add_argument('--bundle-id', default='dev.thinqclimate.widget')
    configure.add_argument('--device', action='append', help='"name|UDID"; replaces the saved list')
    args = parser.parse_args()

    if args.command == 'review-state':
        print(review_state(client_from_env(), os.environ.get('IOS_BUNDLE_ID', 'dev.thinqclimate.widget')))
    elif args.command == 'prepare-adhoc':
        count = prepare_adhoc(client_from_env(), os.environ['IOS_SIGNING_DIR'], os.environ['IOS_TEAM_ID'],
                              os.environ['IOS_BUNDLE_ID'], os.environ['IOS_DISTRIBUTION_CERT_ID'],
                              os.environ['IOS_ADHOC_DEVICES'])
        print(f'Prepared Ad Hoc profiles for {count} device(s)')
    elif args.command == 'manifest':
        Path(args.out).write_bytes(manifest(args.ipa_url, args.bundle_id, args.bundle_version, args.title))
    else:
        setup(args)


if __name__ == '__main__':
    sys.exit(main())
