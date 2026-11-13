#!/usr/bin/env python3
"""Tests for the App Store listing and submission script against an in-memory fake API."""
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import appstore  # noqa: E402

CONTACT = {'firstName': 'Tester', 'lastName': 'Kim', 'phone': '+820000000000', 'email': 'review@example.com'}


class FakeClient:
    base = 'https://api.example'

    def __init__(self, versions=None, priced=False, open_submission=None):
        self.calls = []
        self.versions = versions if versions is not None else [
            {'id': 'v1', 'attributes': {'versionString': '1.0', 'appVersionState': 'PREPARE_FOR_SUBMISSION', 'createdDate': '2026-11-01'}}]
        self.priced = priced
        self.open_submission = open_submission
        self.shots = 0

    def get(self, path, **params):
        query = {f'filter[{k[7:]}]' if k.startswith('filter_') else k: v for k, v in params.items()}
        return self.request('GET', path, query)['data']

    def request(self, method, path, params=None, body=None):
        self.calls.append((method, path, body))
        routes = {
            ('GET', '/v1/apps'): [{'id': 'app'}],
            ('GET', '/v1/apps/app'): {'attributes': {'primaryLocale': 'ko'}},
            ('GET', '/v1/builds'): [{'id': 'b9', 'attributes': {'version': '9', 'processingState': 'VALID'}}],
            ('GET', '/v1/builds/b9/preReleaseVersion'): {'attributes': {'version': '1.5.0'}},
            ('GET', '/v1/apps/app/appInfos'): [{'id': 'info', 'attributes': {'state': 'PREPARE_FOR_SUBMISSION'}}],
            ('GET', '/v1/appInfos/info/appInfoLocalizations'): [{'id': 'il-ko', 'attributes': {'locale': 'ko', 'name': 'Climate Widget'}}],
            ('GET', '/v1/appInfos/info/ageRatingDeclaration'): {'id': 'age', 'attributes': {
                'violenceRealistic': None, 'gambling': None, 'kidsAgeBand': None, 'somethingNew': None}},
            ('GET', '/v1/apps/app/appStoreVersions'): self.versions,
            ('GET', '/v1/apps/app/appPricePoints'): [{'id': 'p1', 'attributes': {'customerPrice': '1100.0'}},
                                                     {'id': 'p0', 'attributes': {'customerPrice': '0.0'}}],
            ('GET', '/v1/territories'): [{'id': 'KOR'}, {'id': 'USA'}],
            ('GET', '/v1/appStoreVersions/v1/appStoreVersionLocalizations'): [{'id': 'vl-ko', 'attributes': {'locale': 'ko'}}],
            ('GET', '/v1/appStoreVersionLocalizations/vl-ko/appScreenshotSets'): [],
            ('GET', '/v1/reviewSubmissions'): [self.open_submission] if self.open_submission else [],
            ('GET', '/v1/reviewSubmissions/rs/items'): [],
        }
        if method == 'GET' and path in ('/v1/apps/app/appPriceSchedule', '/v1/apps/app/appAvailabilityV2'):
            if self.priced:
                return {'data': {'id': 'x'}}
            raise SystemExit(f'App Store Connect GET {path} failed: HTTP 404 Not found')
        if method == 'GET' and path == '/v1/appStoreVersions/v1/appStoreReviewDetail':
            raise SystemExit(f'App Store Connect GET {path} failed: HTTP 404 Not found')
        if method == 'GET' and path.startswith('/v1/appScreenshots/'):
            return {'data': {'attributes': {'assetDeliveryState': {'state': 'COMPLETE'}}}}
        if (method, path) in routes:
            return {'data': routes[(method, path)]}
        if method == 'POST' and path == '/v1/appScreenshots':
            self.shots += 1
            return {'data': {'id': f's{self.shots}', 'attributes': {'uploadOperations': []}}}
        if method == 'POST':
            return {'data': {'id': {'/v1/reviewSubmissions': 'rs', '/v1/appScreenshotSets': 'set'}.get(path, 'new'), 'attributes': {}}}
        if method == 'PATCH' and path == '/v1/reviewSubmissions/rs':
            return {'data': {'attributes': {'state': 'WAITING_FOR_REVIEW'}}}
        if method in ('PATCH', 'DELETE'):
            return {}
        raise AssertionError(f'Unexpected {method} {path}')

    def bodies(self, method, path):
        return [b for m, p, b in self.calls if m == method and p == path]


class PublishTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        for locale in ('ko', 'en-US'):
            (root / 'metadata' / locale).mkdir(parents=True)
            for name in ('description', 'keywords', 'promotional_text', 'subtitle', 'release_notes'):
                (root / 'metadata' / locale / f'{name}.txt').write_text(f'{locale} {name}\n')
        (root / 'screenshots' / 'iphone').mkdir(parents=True)
        (root / 'screenshots' / 'iphone' / '1-home.png').write_bytes(b'png')
        (root / 'review_notes.txt').write_text('Use sample data.')
        patcher = mock.patch.object(appstore, 'ROOT', root)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.addCleanup(self.tmp.cleanup)

    def test_first_release_fills_everything_and_submits(self):
        client = FakeClient()
        appstore.publish(client, 'bundle', '', CONTACT)
        version = client.bodies('PATCH', '/v1/appStoreVersions/v1')[0]['data']['attributes']
        self.assertEqual(version['versionString'], '1.5.0')
        self.assertEqual(version['releaseType'], 'AFTER_APPROVAL')
        self.assertEqual(client.bodies('PATCH', '/v1/appStoreVersions/v1/relationships/build')[0]['data']['id'], 'b9')
        age = client.bodies('PATCH', '/v1/ageRatingDeclarations/age')[0]['data']['attributes']
        self.assertEqual(age, {'violenceRealistic': 'NONE', 'gambling': False})
        price = client.bodies('POST', '/v1/appPriceSchedules')[0]
        self.assertEqual(price['included'][0]['relationships']['appPricePoint']['data']['id'], 'p0')
        self.assertEqual(len(client.bodies('POST', '/v2/appAvailabilities')[0]['included']), 2)
        ko = client.bodies('PATCH', '/v1/appStoreVersionLocalizations/vl-ko')[0]['data']['attributes']
        self.assertNotIn('whatsNew', ko, 'A first version cannot have release notes')
        self.assertEqual(ko['description'], 'ko description')
        en = client.bodies('POST', '/v1/appStoreVersionLocalizations')[0]['data']['attributes']
        self.assertEqual(en['locale'], 'en-US')
        info_en = client.bodies('POST', '/v1/appInfoLocalizations')[0]['data']['attributes']
        self.assertEqual(info_en['name'], 'Climate Widget')
        self.assertEqual(client.shots, 1)
        self.assertEqual(client.bodies('PATCH', '/v1/appScreenshots/s1')[0]['data']['attributes']['uploaded'], True)
        review = client.bodies('POST', '/v1/appStoreReviewDetails')[0]['data']['attributes']
        self.assertEqual(review['contactEmail'], 'review@example.com')
        self.assertFalse(review['demoAccountRequired'])
        self.assertEqual(client.bodies('PATCH', '/v1/reviewSubmissions/rs')[0]['data']['attributes'], {'submitted': True})

    def test_update_keeps_price_and_adds_release_notes(self):
        client = FakeClient(versions=[
            {'id': 'old', 'attributes': {'versionString': '1.4.0', 'appVersionState': 'READY_FOR_SALE', 'createdDate': '2026-10-01'}},
            {'id': 'v1', 'attributes': {'versionString': '1.5.0', 'appVersionState': 'PREPARE_FOR_SUBMISSION', 'createdDate': '2026-11-01'}}],
            priced=True)
        appstore.publish(client, 'bundle', '9', CONTACT, screenshots=False)
        self.assertEqual(len(client.bodies('POST', '/v1/appPriceSchedules')), 1, 'The price is always reset to free')
        self.assertFalse(client.bodies('POST', '/v2/appAvailabilities'))
        ko = client.bodies('PATCH', '/v1/appStoreVersionLocalizations/vl-ko')[0]['data']['attributes']
        self.assertEqual(ko['whatsNew'], 'ko release_notes')
        self.assertNotIn('versionString', client.bodies('PATCH', '/v1/appStoreVersions/v1')[0]['data']['attributes'])

    def test_released_version_is_not_resubmitted(self):
        client = FakeClient(versions=[
            {'id': 'v1', 'attributes': {'versionString': '1.5.0', 'appVersionState': 'READY_FOR_SALE', 'createdDate': '2026-11-01'}}])
        with self.assertRaises(SystemExit) as caught:
            appstore.publish(client, 'bundle', '9', CONTACT)
        self.assertIn('MARKETING_VERSION', str(caught.exception))

    def test_version_in_review_blocks_a_new_submission(self):
        client = FakeClient(versions=[
            {'id': 'v1', 'attributes': {'versionString': '1.5.0', 'appVersionState': 'WAITING_FOR_REVIEW', 'createdDate': '2026-11-01'}}])
        appstore.publish(client, 'bundle', '9', CONTACT)
        self.assertFalse([c for c in client.calls if c[0] != 'GET'], 'Nothing is changed while a version is in review')

    def test_waiting_submission_is_left_alone(self):
        client = FakeClient(priced=True, open_submission={'id': 'rs', 'attributes': {'state': 'WAITING_FOR_REVIEW'}})
        appstore.publish(client, 'bundle', '9', CONTACT, screenshots=False)
        self.assertFalse(client.bodies('PATCH', '/v1/reviewSubmissions/rs'))

    def test_state_uses_newest_version(self):
        client = FakeClient(versions=[
            {'id': 'a', 'attributes': {'versionString': '1.5.0', 'appVersionState': 'WAITING_FOR_REVIEW', 'createdDate': '2026-11-13'}},
            {'id': 'b', 'attributes': {'versionString': '1.0', 'appVersionState': 'READY_FOR_SALE', 'createdDate': '2026-01-01'}}])
        self.assertEqual(appstore.store_state(client, 'bundle'), ('WAITING_FOR_REVIEW', '1.5.0', 'app'))


if __name__ == '__main__':
    unittest.main()
