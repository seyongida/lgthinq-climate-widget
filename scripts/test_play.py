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

    def call(self, method, url, body=None, data=None, content_type='application/json'):
        self.calls.append((method, url.split('/pkg')[-1], body, content_type))
        if url.endswith('/bundles?uploadType=media'):
            return {'versionCode': 8}
        if '/tracks/' in url and self.draft_only and body['releases'][0]['status'] != 'draft':
            raise SystemExit('Google Play PUT failed: HTTP 400 Only releases with status draft may be created on draft app.')
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
        self.assertEqual(play.upload_bundle(fake, 'e1', self.bundle, 'alpha', '새 버전'), (8, 'completed'))
        track = [c for c in fake.calls if '/tracks/alpha' in c[1]][0][2]
        self.assertEqual(track['releases'][0]['versionCodes'], ['8'])
        self.assertEqual(track['releases'][0]['releaseNotes'][0]['language'], 'ko-KR')

    def test_unpublished_app_gets_a_draft_release(self):
        fake = FakePlay(draft_only=True)
        self.assertEqual(play.upload_bundle(fake, 'e1', self.bundle, 'alpha', ''), (8, 'draft'))


if __name__ == '__main__':
    unittest.main()
