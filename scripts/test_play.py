#!/usr/bin/env python3
"""Tests for the Google Play upload script with a recording fake instead of the network."""
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import play  # noqa: E402


class FakePlay(play.Play):
    def __init__(self, draft_only=False):
        super().__init__('token', 'pkg')
        self.calls, self.draft_only = [], draft_only

    def call(self, method, url, body=None, data=None, content_type='application/json', raw=False):
        self.calls.append((method, url.split('/pkg')[-1], body, content_type))
        if '/generatedApks/' in url:
            if url.endswith('?alt=media'):
                return b'signed-apk'
            self.polls = getattr(self, 'polls', 0) + 1
            if self.polls == 1:
                raise SystemExit('Google Play GET failed: HTTP 404 not ready')
            return {'generatedApks': [{'certificateSha256Hash': 'abc', 'generatedUniversalApk': {'downloadId': 'd1'}}]}
        if url.endswith('/bundles?uploadType=media'):
            return {'versionCode': 8}
        if '/tracks/' in url:
            self.status = body['releases'][0]['status']
        # Play reports the draft-only rule when the edit is committed.
        if url.endswith(':commit') and self.draft_only and getattr(self, 'status', '') != 'draft':
            raise SystemExit('Google Play POST commit failed: HTTP 400 Only releases with status draft may be created on draft app.')
        return {'id': 'e1'}


class PlayTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        ko = self.root / 'metadata' / 'ko-KR'
        ko.mkdir(parents=True)
        (ko / 'title.txt').write_text('Climate Widget\n')
        (ko / 'short_description.txt').write_text('짧은 설명')
        (ko / 'full_description.txt').write_text('긴 설명')
        phone = self.root / 'images' / 'ko-KR' / 'phone'
        phone.mkdir(parents=True)
        for name in ('2.png', '1.png'):
            (phone / name).write_bytes(b'png')
        self.bundle = self.root / 'app.aab'
        self.bundle.write_bytes(b'aab')

    def test_listing_replaces_texts_and_images_in_order(self):
        fake = FakePlay()
        languages, counts = play.sync_listing(fake, 'e1', self.root)
        self.assertEqual(languages, ['ko-KR'])
        self.assertEqual(counts, {'ko-KR/phoneScreenshots': 2})
        put = next(c for c in fake.calls if c[0] == 'PUT')
        self.assertEqual(put[2]['title'], 'Climate Widget')
        methods = [c[0] for c in fake.calls]
        self.assertLess(methods.index('DELETE'), methods.index('POST'), 'Old screenshots are removed first')

    def test_too_long_title_is_rejected(self):
        (self.root / 'metadata' / 'ko-KR' / 'title.txt').write_text('x' * 31)
        with self.assertRaises(SystemExit) as caught:
            play.sync_listing(FakePlay(), 'e1', self.root)
        self.assertIn('limit is 30', str(caught.exception))

    def test_bundle_goes_to_track_with_notes(self):
        fake = FakePlay()
        self.assertEqual(play.publish(fake, self.bundle, 'alpha', '새 버전', listing=False), (8, 'completed', None))
        track = [c for c in fake.calls if '/tracks/alpha' in c[1]][0][2]
        self.assertEqual(track['releases'][0]['versionCodes'], ['8'])
        self.assertEqual(track['releases'][0]['releaseNotes'][0]['language'], 'ko-KR')

    def test_unpublished_app_gets_a_draft_release_even_when_commit_refuses(self):
        fake = FakePlay(draft_only=True)
        self.assertEqual(play.publish(fake, self.bundle, 'alpha', '', listing=False)[:2], (8, 'draft'))
        commits = [c for c in fake.calls if c[1].endswith(':commit')]
        self.assertEqual(len(commits), 2, 'The refused edit is replaced by a new draft edit')


class SignedApkTests(unittest.TestCase):
    def test_waits_for_google_and_saves_the_signed_apk(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / 'app.apk'
            fake = FakePlay()
            self.assertEqual(play.universal_apk(fake, 9, out, wait_seconds=60, poll=0), 'abc')
            self.assertEqual(out.read_bytes(), b'signed-apk')
            self.assertTrue(fake.calls[-1][1].endswith('/generatedApks/9/downloads/d1:download?alt=media'))


if __name__ == '__main__':
    unittest.main()
