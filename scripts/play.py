#!/usr/bin/env python3
"""Google Play: uploads an app bundle to a testing track and keeps the store listing in sync with play/.

Uses a service account key from PLAY_SERVICE_ACCOUNT_BASE64 (or --key). The service account must be
invited in Play Console → Users and permissions with release and store listing permissions.
Never prints the key.
"""
import argparse
import base64
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.request

sys.path.insert(0, str(Path(__file__).resolve().parent))
import asc  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent / 'play'
API = os.environ.get('PLAY_API_BASE', 'https://androidpublisher.googleapis.com')
SCOPE = 'https://www.googleapis.com/auth/androidpublisher'
PACKAGE = 'dev.thinqclimate.widget.release'
# Folder name under play/images/<language>/ → Play image type.
IMAGE_TYPES = {'icon': 'icon', 'feature': 'featureGraphic', 'phone': 'phoneScreenshots'}


class Play:
    def __init__(self, token, package=PACKAGE):
        self.token, self.package = token, package
        self.base = f'{API}/androidpublisher/v3/applications/{package}'
        self.upload_base = f'{API}/upload/androidpublisher/v3/applications/{package}'

    def call(self, method, url, body=None, data=None, content_type='application/json', raw=False):
        want_raw = raw
        payload = data if data is not None else (json.dumps(body).encode() if body is not None else None)
        request = urllib.request.Request(url, method=method, data=payload)
        request.add_header('Authorization', 'Bearer ' + self.token)
        if payload is not None:
            request.add_header('Content-Type', content_type)
        try:
            with urllib.request.urlopen(request, timeout=300) as response:
                body_bytes = response.read()
        except urllib.error.HTTPError as error:
            try:
                message = json.loads(error.read()).get('error', {}).get('message', '')
            except ValueError:
                message = ''
            raise SystemExit(f'Google Play {method} {url.split(self.package)[-1] or "/"} failed: HTTP {error.code} {message[:300]}')
        if want_raw:
            return body_bytes
        return json.loads(body_bytes) if body_bytes else {}

    def edit(self):
        return self.call('POST', f'{self.base}/edits', {})['id']

    def commit(self, edit):
        return self.call('POST', f'{self.base}/edits/{edit}:commit')


def listing(locale_dir):
    read = lambda name: (locale_dir / name).read_text(encoding='utf-8').strip()
    return {'language': locale_dir.name, 'title': read('title.txt'), 'shortDescription': read('short_description.txt'),
            'fullDescription': read('full_description.txt')}


def sync_listing(play, edit, root=None):
    """Replaces texts and images for every language folder under play/metadata and play/images."""
    root = root or ROOT
    languages = []
    for locale_dir in sorted((root / 'metadata').iterdir()):
        if not locale_dir.is_dir():
            continue
        data = listing(locale_dir)
        for field, limit in (('title', 30), ('shortDescription', 80), ('fullDescription', 4000)):
            if len(data[field]) > limit:
                raise SystemExit(f'{locale_dir.name} {field} is {len(data[field])} characters; the limit is {limit}')
        play.call('PUT', f'{play.base}/edits/{edit}/listings/{locale_dir.name}', data)
        languages.append(locale_dir.name)
    counts = {}
    for locale_dir in sorted((root / 'images').iterdir()) if (root / 'images').exists() else []:
        for folder, image_type in IMAGE_TYPES.items():
            files = sorted((locale_dir / folder).glob('*.png'))
            if not files:
                continue
            play.call('DELETE', f'{play.base}/edits/{edit}/listings/{locale_dir.name}/{image_type}')
            for path in files:
                play.call('POST', f'{play.upload_base}/edits/{edit}/listings/{locale_dir.name}/{image_type}?uploadType=media',
                          data=path.read_bytes(), content_type='image/png')
            counts[f'{locale_dir.name}/{image_type}'] = len(files)
    details = root / 'details.json'
    if details.exists():
        play.call('PUT', f'{play.base}/edits/{edit}/details', json.loads(details.read_text(encoding='utf-8')))
    return languages, counts


def upload_bundle(play, edit, bundle, track, notes, status='completed'):
    uploaded = play.call('POST', f'{play.upload_base}/edits/{edit}/bundles?uploadType=media',
                         data=Path(bundle).read_bytes(), content_type='application/octet-stream')
    code = uploaded['versionCode']
    release = {'versionCodes': [str(code)], 'status': status}
    if notes:
        release['releaseNotes'] = [{'language': 'ko-KR', 'text': notes[:500]}]
    play.call('PUT', f'{play.base}/edits/{edit}/tracks/{track}', {'track': track, 'releases': [release]})
    return code


def publish(play, bundle, track, notes, listing=True):
    """Uploads and commits in one edit. An app that has never been published only accepts draft releases
    (Play may say so when the track is set or only at commit), so that case is retried as a draft, which
    goes live from Play Console."""
    for status in ('completed', 'draft'):
        edit = play.edit()
        try:
            code = upload_bundle(play, edit, bundle, track, notes, status)
            counts = sync_listing(play, edit) if listing else None
            play.commit(edit)
            return code, status, counts
        except SystemExit as error:
            if status == 'draft' or 'draft' not in str(error).lower():
                raise
            print('Play accepts only draft releases until the app is published; uploading as a draft')


def universal_apk(play, version_code, out, wait_seconds=1200, poll=30):
    """Downloads the universal APK that Google Play builds from the bundle and signs with the app signing key.

    The app signing key never leaves Google, so this is how the GitHub download gets the same signature as
    the Play install. Returns the signing certificate's SHA-256.
    """
    import time
    deadline = time.time() + wait_seconds
    while True:
        try:
            found = play.call('GET', f'{play.base}/generatedApks/{version_code}').get('generatedApks', [])
        except SystemExit as error:
            if 'HTTP 404' not in str(error):
                raise
            found = []
        ready = [g for g in found if g.get('generatedUniversalApk', {}).get('downloadId')]
        if ready:
            break
        if time.time() > deadline:
            raise SystemExit(f'Google Play has not built the universal APK for version code {version_code} yet')
        time.sleep(poll)
    download = ready[0]['generatedUniversalApk']['downloadId']
    data = play.call('GET', f'{play.base}/generatedApks/{version_code}/downloads/{download}:download?alt=media', raw=True)
    Path(out).write_bytes(data)
    return ready[0].get('certificateSha256Hash', '')


def load_key(path):
    """The Play key, or the Firebase service account already saved for iOS distribution."""
    if path:
        return json.loads(Path(path).expanduser().read_text())
    raw = os.environ.get('PLAY_SERVICE_ACCOUNT_BASE64') or os.environ.get('FIREBASE_SERVICE_ACCOUNT_BASE64', '')
    if not raw:
        raise SystemExit('Missing PLAY_SERVICE_ACCOUNT_BASE64 (or FIREBASE_SERVICE_ACCOUNT_BASE64)')
    return json.loads(base64.b64decode(raw, validate=True))


def prepare(key, package):
    """Turns on the Play Developer API in the key's project and reports whether Play Console access is granted."""
    project, email = key.get('project_id', ''), key.get('client_email', '')
    print('Service account to invite in Play Console:', email)
    url = f'https://serviceusage.googleapis.com/v1/projects/{project}/services/androidpublisher.googleapis.com:enable'
    request = urllib.request.Request(url, method='POST', data=b'{}')
    request.add_header('Authorization', 'Bearer ' + asc.google_token(key))
    request.add_header('Content-Type', 'application/json')
    try:
        urllib.request.urlopen(request, timeout=60).read()
        print('Google Play Android Developer API: enabled')
    except urllib.error.HTTPError as error:
        print(f'Google Play Android Developer API: could not enable (HTTP {error.code}). Enable it here: '
              f'https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com?project={project}')
    play = Play(asc.google_token(key, SCOPE), package)
    try:
        play.call('DELETE', f'{play.base}/edits/{play.edit()}')
        print(f'Play Console access to {package}: OK')
        return True
    except SystemExit as error:
        print(f'Play Console access to {package}: not yet ({str(error)[-160:]})')
        return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--key', help='Service account JSON key (default: PLAY_SERVICE_ACCOUNT_BASE64)')
    parser.add_argument('--package', default=os.environ.get('PLAY_PACKAGE', PACKAGE))
    commands = parser.add_subparsers(dest='command', required=True)
    up = commands.add_parser('upload', help='Upload an .aab to a track and sync the listing')
    up.add_argument('bundle')
    up.add_argument('--track', default=os.environ.get('PLAY_TRACK') or 'alpha', help='internal, alpha (closed), beta (open) or production')
    up.add_argument('--notes', default='')
    up.add_argument('--no-listing', action='store_true')
    commands.add_parser('listing', help='Only sync the store listing')
    commands.add_parser('prepare', help='Enable the API and check Play Console access for the saved key')
    commands.add_parser('tracks', help='List tracks, their releases and the countries each one targets')
    apk = commands.add_parser('universal-apk', help='Download the Google-signed universal APK of a version code')
    apk.add_argument('version_code')
    apk.add_argument('out')
    save = commands.add_parser('save-key', help='Check a service account key against the app and save it to GitHub')
    save.add_argument('file')
    args = parser.parse_args()

    if args.command == 'save-key':
        key = json.loads(Path(args.file).expanduser().read_text())
        if key.get('type') != 'service_account' or 'private_key' not in key:
            raise SystemExit('This is not a service account key file')
        play = Play(asc.google_token(key, SCOPE), args.package)
        play.call('DELETE', f'{play.base}/edits/{play.edit()}')  # Proves the account can open the app.
        asc.gh_secret('PLAY_SERVICE_ACCOUNT_BASE64', base64.b64encode(Path(args.file).expanduser().read_bytes()).decode())
        print(f'Saved PLAY_SERVICE_ACCOUNT_BASE64 ({key.get("client_email", "?").split("@")[0]}@…) with access to {args.package}')
        return
    if args.command == 'prepare':
        sys.exit(0 if prepare(load_key(args.key), args.package) else 1)
    play = Play(asc.google_token(load_key(args.key), SCOPE), args.package)
    if args.command == 'tracks':
        edit = play.edit()
        for track in play.call('GET', f'{play.base}/edits/{edit}/tracks').get('tracks', []):
            name = track['track']
            try:
                countries = play.call('GET', f'{play.base}/edits/{edit}/countryAvailability/{name}')
                where = 'all countries' if countries.get('restOfWorld') else ', '.join(c['countryCode'] for c in countries.get('countries', [])) or 'none'
            except SystemExit as error:
                where = 'unknown (' + str(error)[-80:] + ')'
            releases = '; '.join(f"{r.get('name', '')} {r.get('versionCodes', [])} {r.get('status')}" for r in track.get('releases', [])) or 'no releases'
            print(f'{name}: countries={where} | {releases}')
        play.call('DELETE', f'{play.base}/edits/{edit}')
        return
    if args.command == 'universal-apk':
        print('Google-signed universal APK, app signing certificate SHA-256:', universal_apk(play, args.version_code, args.out))
        return
    if args.command == 'upload':
        code, status, counts = publish(play, args.bundle, args.track, args.notes, not args.no_listing)
        print(f'Uploaded version code {code} to the {args.track} track ({status})')
        if counts:
            print('Listing:', ', '.join(counts[0]), ' '.join(f'{k}={v}' for k, v in counts[1].items()))
        print('Committed')
        return
    edit = play.edit()
    languages, counts = sync_listing(play, edit)
    print('Listing:', ', '.join(languages), ' '.join(f'{k}={v}' for k, v in counts.items()))
    play.commit(edit)
    print('Committed')

if __name__ == '__main__':
    main()
