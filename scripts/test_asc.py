#!/usr/bin/env python3
"""Tests for the Ad Hoc tooling, run against a local stub of the App Store Connect API."""
import base64
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest import mock
from urllib.parse import parse_qs, urlparse

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
import asc  # noqa: E402

UDID_NEW = '00008110-000000000000001E'
UDID_KNOWN = 'a' * 40


class Stub(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, status, body=None):
        data = json.dumps(body).encode() if body is not None else b''
        self.send_response(status)
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def handle_any(self):
        state = self.server.state
        length = int(self.headers.get('Content-Length') or 0)
        raw = self.rfile.read(length) if length else b''
        try:
            body = json.loads(raw) if raw else None
        except ValueError:
            body = raw.decode()  # The token request is form-encoded.
        url = urlparse(self.path)
        query = {key: values[0] for key, values in parse_qs(url.query).items()}
        state['requests'].append((self.command, url.path, query, body))
        state['auth'].append(self.headers.get('Authorization', ''))
        key = (self.command, url.path)
        if key == ('GET', '/v1/apps'):
            return self.reply(200, {'data': [{'id': 'app1'}]})
        if key == ('GET', '/v1/builds'):
            return self.reply(200, {'data': [{'id': 'b7', 'attributes': {'version': '7'}}, {'id': 'b6', 'attributes': {'version': '12'}},
                                             {'id': 'bx', 'attributes': {'version': '1.0.1'}}]})
        if key == ('GET', '/v1/betaAppReviewSubmissions'):
            found = state['reviews'].get(query['filter[build]'])
            return self.reply(200, {'data': [{'attributes': {'betaReviewState': found}}] if found else []})
        if key == ('GET', '/v1/devices') and 'filter[udid]' in query:
            known = query['filter[udid]'] == UDID_KNOWN
            return self.reply(200, {'data': [{'id': 'dev-known', 'attributes': {'status': 'ENABLED'}}] if known else []})
        if key == ('GET', '/v1/devices') and query.get('filter[platform]') == 'IOS' and 'filter[udid]' not in query:
            return self.reply(200, {'data': [
                {'id': '1', 'attributes': {'name': '내 iPhone', 'udid': UDID_KNOWN, 'deviceClass': 'IPHONE', 'status': 'ENABLED'}},
                {'id': '2', 'attributes': {'name': 'Mac', 'udid': 'B' * 40, 'deviceClass': 'MAC', 'status': 'ENABLED'}},
                {'id': '3', 'attributes': {'name': 'i|Pad', 'udid': UDID_NEW, 'deviceClass': 'IPAD', 'status': 'ENABLED'}}]})
        if key == ('POST', '/token'):
            return self.reply(200, {'access_token': 'stub-token'})
        if self.command == 'GET' and url.path.endswith('/groups'):
            if url.path != '/v1/projects/439215807830/groups':
                return self.reply(403, {'error': {'message': 'Firebase App Distribution API has not been used in project 917123264746 before'}})
            return self.reply(200, {'groups': [{'name': 'projects/439215807830/groups/friends'}]})
        if key == ('POST', '/v1/devices'):
            return self.reply(201, {'data': {'id': 'dev-new'}})
        if key == ('GET', '/v1/bundleIds'):
            identifier = query['filter[identifier]']
            items = [{'id': 'wrong', 'attributes': {'identifier': identifier + '.other'}},
                     {'id': 'bundle-' + identifier.rsplit('.', 1)[-1], 'attributes': {'identifier': identifier}}]
            return self.reply(200, {'data': items})
        if key == ('GET', '/v1/profiles'):
            name = query['filter[name]']
            return self.reply(200, {'data': [{'id': 'old-' + name.rsplit(' ', 1)[-1], 'attributes': {'name': name}},
                                             {'id': 'keep', 'attributes': {'name': name + ' copy'}}]})
        if self.command == 'DELETE':
            return self.reply(204)
        if key == ('POST', '/v1/profiles'):
            name = body['data']['attributes']['name']
            content = base64.b64encode(b'profile ' + name.encode()).decode()
            return self.reply(201, {'data': {'attributes': {'name': name, 'uuid': 'uuid-' + name[-3:], 'profileContent': content}}})
        if key == ('POST', '/v1/certificates'):
            with tempfile.TemporaryDirectory() as directory:
                csr = Path(directory) / 'csr.pem'
                csr.write_text(body['data']['attributes']['csrContent'])
                subprocess.run([asc.OPENSSL, 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', f'{directory}/ca.key',
                                '-out', f'{directory}/ca.pem', '-subj', '/CN=Stub CA', '-days', '2'], check=True, capture_output=True)
                der = subprocess.run([asc.OPENSSL, 'x509', '-req', '-in', str(csr), '-CA', f'{directory}/ca.pem', '-CAkey', f'{directory}/ca.key',
                                      '-CAcreateserial', '-days', '2', '-outform', 'der'], check=True, capture_output=True).stdout
            state['certificate_body'] = body
            return self.reply(201, {'data': {'id': 'cert9', 'attributes': {'certificateContent': base64.b64encode(der).decode()}}})
        return self.reply(404, {'errors': [{'title': 'Not stubbed'}]})

    do_GET = do_POST = do_DELETE = handle_any


class AscTests(unittest.TestCase):
    def setUp(self):
        self.temp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.temp)
        self.key = self.temp / 'key.p8'
        subprocess.run(['openssl', 'ecparam', '-name', 'prime256v1', '-genkey', '-noout', '-out', str(self.temp / 'ec.pem')], check=True)
        subprocess.run(['openssl', 'pkcs8', '-topk8', '-nocrypt', '-in', str(self.temp / 'ec.pem'), '-out', str(self.key)], check=True)
        self.server = HTTPServer(('127.0.0.1', 0), Stub)
        self.server.state = {'requests': [], 'auth': [], 'reviews': {}}
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)
        base = f'http://127.0.0.1:{self.server.server_port}'
        patcher = mock.patch.dict(os.environ, {'ASC_API_BASE': base, 'HOME': str(self.temp)})
        patcher.start()
        self.addCleanup(patcher.stop)
        self.client = asc.Client(str(self.key), 'KEY123', 'issuer-1')

    def test_token_has_required_claims_and_verifiable_signature(self):
        token = asc.make_token(self.key, 'KEY123', 'issuer-1', now=1000)
        header, payload, signature = token.split('.')
        pad = lambda text: base64.urlsafe_b64decode(text + '=' * (-len(text) % 4))
        self.assertEqual(json.loads(pad(header)), {'alg': 'ES256', 'kid': 'KEY123', 'typ': 'JWT'})
        self.assertEqual(json.loads(pad(payload)), {'iss': 'issuer-1', 'iat': 1000, 'exp': 2200, 'aud': 'appstoreconnect-v1'})
        raw = pad(signature)
        self.assertEqual(len(raw), 64)

        def integer(value):
            value = value.lstrip(b'\0') or b'\0'
            value = b'\0' + value if value[0] & 0x80 else value
            return bytes([2, len(value)]) + value
        content = integer(raw[:32]) + integer(raw[32:])
        (self.temp / 'sig').write_bytes(bytes([0x30, len(content)]) + content)
        subprocess.run(['openssl', 'pkey', '-in', str(self.key), '-pubout', '-out', str(self.temp / 'pub.pem')], check=True)
        result = subprocess.run(['openssl', 'dgst', '-sha256', '-verify', str(self.temp / 'pub.pem'), '-signature', str(self.temp / 'sig')],
                                input=f'{header}.{payload}'.encode(), capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_review_state_uses_newest_submitted_build(self):
        self.assertEqual(asc.review_state(self.client, 'x.y'), 'NONE')
        self.server.state['reviews']['b6'] = 'REJECTED'
        self.assertEqual(asc.review_state(self.client, 'x.y'), 'REJECTED')
        self.server.state['reviews']['b7'] = 'APPROVED'
        self.assertEqual(asc.review_state(self.client, 'x.y'), 'APPROVED')
        builds = [r for r in self.server.state['requests'] if r[1] == '/v1/builds'][0]
        self.assertEqual(builds[2]['filter[app]'], 'app1')
        self.assertEqual(builds[2]['sort'], '-uploadedDate')
        self.assertTrue(all(a.startswith('Bearer ') for a in self.server.state['auth']))

    def test_prepare_adhoc_limits_profiles_to_configured_devices(self):
        devices = f'Known|{UDID_KNOWN}\nFriend iPhone|{UDID_NEW}\n'
        signing = self.temp / 'signing'
        self.assertEqual(asc.prepare_adhoc(self.client, signing, 'TEAM', 'dev.app', 'cert1', devices), 2)
        requests = self.server.state['requests']
        registered = [r[3] for r in requests if r[:2] == ('POST', '/v1/devices')]
        self.assertEqual(registered, [{'data': {'type': 'devices', 'attributes': {'name': 'Friend iPhone', 'platform': 'IOS', 'udid': UDID_NEW}}}])
        deleted = [r[1] for r in requests if r[0] == 'DELETE']
        self.assertEqual(deleted, ['/v1/profiles/old-app', '/v1/profiles/old-widget'])
        created = [r[3]['data'] for r in requests if r[:2] == ('POST', '/v1/profiles')]
        self.assertEqual([c['attributes']['profileType'] for c in created], ['IOS_APP_ADHOC'] * 2)
        self.assertEqual([c['relationships']['bundleId']['data']['id'] for c in created], ['bundle-app', 'bundle-extension'])
        for item in created:
            self.assertEqual([d['id'] for d in item['relationships']['devices']['data']], ['dev-known', 'dev-new'])
            self.assertEqual(item['relationships']['certificates']['data'], [{'type': 'certificates', 'id': 'cert1'}])
        export = plistlib.loads((signing / 'ExportOptions.plist').read_bytes())
        self.assertEqual(export['method'], 'release-testing')
        self.assertEqual(export['provisioningProfiles'], {'dev.app': 'ThinQ AdHoc app', 'dev.app.extension': 'ThinQ AdHoc widget'})
        profiles = json.loads((signing / 'profiles.json').read_text())
        self.assertEqual(Path(profiles['app']['paths'][0]).read_bytes(), b'profile ThinQ AdHoc app')

    def test_invalid_device_lists_are_rejected_without_echoing_them(self):
        for text in ['', 'no-separator', 'Name|short', f'|{UDID_NEW}']:
            with self.assertRaises(SystemExit) as caught:
                asc.parse_devices(text)
            self.assertNotIn('short', str(caught.exception))

    def test_api_error_reports_status_without_secrets(self):
        with self.assertRaises(SystemExit) as caught:
            self.client.request('GET', '/v1/unknown')
        self.assertIn('HTTP 404 Not stubbed', str(caught.exception))
        self.assertNotIn('Bearer', str(caught.exception))

    def test_created_certificate_exports_an_importable_p12(self):
        certificate_id, p12, password = asc.create_certificate(self.client)
        self.assertEqual(certificate_id, 'cert9')
        attributes = self.server.state['certificate_body']['data']['attributes']
        self.assertEqual(attributes['certificateType'], 'DISTRIBUTION')
        self.assertIn('BEGIN CERTIFICATE REQUEST', attributes['csrContent'])
        if not shutil.which('security'):
            self.skipTest('macOS keychain tools are unavailable')
        path = self.temp / 'dist.p12'
        path.write_bytes(p12)
        keychain = self.temp / 'test.keychain-db'
        subprocess.run(['security', 'create-keychain', '-p', 'test', str(keychain)], check=True)
        self.addCleanup(subprocess.run, ['security', 'delete-keychain', str(keychain)], capture_output=True)
        subprocess.run(['security', 'unlock-keychain', '-p', 'test', str(keychain)], check=True)
        # Same import the Ad Hoc build runs on CI.
        result = subprocess.run(['security', 'import', str(path), '-P', password, '-T', '/usr/bin/codesign', '-t', 'cert',
                                 '-f', 'pkcs12', '-k', str(keychain)], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        identities = subprocess.run(['security', 'find-identity', str(keychain)], capture_output=True, text=True).stdout
        self.assertIn('ThinQ Climate Distribution', identities)

    def test_apple_registered_devices_merge_with_firebase(self):
        apple = asc.apple_devices(self.client)
        self.assertEqual(apple, [('내 iPhone', UDID_KNOWN), ('i Pad', UDID_NEW)])
        query = [r for r in self.server.state['requests'] if r[1] == '/v1/devices'][0][2]
        self.assertEqual((query['filter[platform]'], query['filter[status]']), ('IOS', 'ENABLED'))
        merged = asc.merge_devices([('친구 iPhone', UDID_NEW.lower())], apple)
        self.assertEqual(merged, [('친구 iPhone', UDID_NEW.lower()), ('내 iPhone', UDID_KNOWN)])

    def test_firebase_key_must_match_the_app_project(self):
        pem = self.temp / 'sa.pem'
        subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', str(pem)], check=True, capture_output=True)
        key = {'type': 'service_account', 'client_email': 'ci@x.iam.gserviceaccount.com', 'private_key': pem.read_text(),
               'token_uri': os.environ['ASC_API_BASE'] + '/token'}
        with mock.patch.dict(os.environ, {'FIREBASE_API_BASE': os.environ['ASC_API_BASE']}):
            asc.check_firebase(key, '1:439215807830:ios:abc', 'friends')
            with self.assertRaises(SystemExit) as wrong:
                asc.check_firebase(key, '1:111:ios:abc', 'friends')
            self.assertIn('917123264746', str(wrong.exception))
            with self.assertRaises(SystemExit) as group:
                asc.check_firebase(key, '1:439215807830:ios:abc', 'friends,family')
            self.assertIn('family', str(group.exception))

    def test_next_build_number_follows_the_highest_upload(self):
        self.assertEqual(asc.next_build(self.client, 'x.y'), 13)

    def test_app_store_profile_has_no_devices(self):
        asc.create_profile(self.client, 'ThinQ AppStore app', 'bundle-app', 'cert1', None, 'IOS_APP_STORE')
        body = [r[3]['data'] for r in self.server.state['requests'] if r[:2] == ('POST', '/v1/profiles')][-1]
        self.assertEqual(body['attributes']['profileType'], 'IOS_APP_STORE')
        self.assertNotIn('devices', body['relationships'])

    def test_firebase_udid_export(self):
        text = ("Device ID\tDevice Name\tDevice Platform\n"
                f"{UDID_NEW}\t민수의 iPhone\tios\n{UDID_KNOWN}\tTab|let\tipados\n"
                "notaudid\tBroken\tios\nABCDEF0123456789ABCDEF0123456789ABCDEF01\tMac\tmacos\n"
                f"{UDID_NEW}\t중복\tios\n")
        self.assertEqual(asc.parse_export(text), [('민수의 iPhone', UDID_NEW), ('Tab let', UDID_KNOWN)])
        self.assertEqual(asc.parse_devices('\n'.join(f'{n}|{u}' for n, u in asc.parse_export(text))), asc.parse_export(text))
        with self.assertRaises(SystemExit):
            asc.parse_export('Device ID\tDevice Name\tDevice Platform\n')

class SiteTests(unittest.TestCase):
    def build(self, **env):
        temp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, temp)
        result = subprocess.run([sys.executable, str(ROOT / 'build-site.py'), str(temp / 'out')], capture_output=True, text=True,
                                env={'PATH': os.environ['PATH'], **env})
        return result, temp / 'out'

    def test_without_adhoc_files_no_direct_install_is_shown(self):
        result, out = self.build()
        self.assertEqual(result.returncode, 0, result.stderr)
        html = (out / 'index.html').read_text()
        self.assertNotIn('itms-services', html)
        self.assertIn('class="pending"', html)
        
class WatcherTests(unittest.TestCase):
    """Runs watch-review.sh with fake gh and asc commands to check its decisions."""

    def run_watch(self, state, runs='', devices='true', certificate='true', key='x', firebase='true'):
        temp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, temp)
        (temp / 'scripts').mkdir()
        (temp / 'scripts/asc.py').write_text(f'print("{state}")\n')
        (temp / 'bin').mkdir()
        gh = temp / 'bin/gh'
        gh.write_text(f'#!/bin/bash\necho "$*" >> "{temp}/gh.log"\nif [[ "$1 $2" == "run list" ]]; then printf "%s" "{runs}"; fi\n')
        gh.chmod(0o755)
        result = subprocess.run(['bash', str(ROOT / 'watch-review.sh')], cwd=temp, capture_output=True, text=True,
                                env={'PATH': f'{temp}/bin:{os.environ["PATH"]}', 'ASC_API_KEY_P8_BASE64': key,
                                     'HAS_DEVICES': devices, 'HAS_CERTIFICATE': certificate, 'HAS_FIREBASE': firebase})
        log = (temp / 'gh.log').read_text() if (temp / 'gh.log').exists() else ''
        return result, 'workflow run ios-adhoc.yml' in log

    def test_rejected_starts_fallback(self):
        result, started = self.run_watch('REJECTED')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(started)

    def test_other_states_do_nothing(self):
        for state in ['NONE', 'WAITING_FOR_REVIEW', 'IN_REVIEW', 'APPROVED']:
            result, started = self.run_watch(state)
            self.assertEqual(result.returncode, 0)
            self.assertFalse(started, state)

    def test_only_one_release_is_started(self):
        result, started = self.run_watch('REJECTED', runs='2026-11-12T01:00:00Z\\n')
        self.assertEqual(result.returncode, 0)
        self.assertFalse(started)

    def test_missing_device_fails_loudly(self):
        result, started = self.run_watch('REJECTED', devices='false')
        self.assertEqual(result.returncode, 1)
        self.assertFalse(started)
        self.assertIn('rejected', result.stdout)

    def test_missing_firebase_fails_loudly(self):
        result, started = self.run_watch('REJECTED', firebase='false')
        self.assertEqual(result.returncode, 1)
        self.assertFalse(started)
        self.assertIn('Firebase', result.stdout)

    def test_without_key_is_a_quiet_no_op(self):
        result, started = self.run_watch('REJECTED', key='')
        self.assertEqual(result.returncode, 0)
        self.assertFalse(started)


if __name__ == '__main__':
    unittest.main()
