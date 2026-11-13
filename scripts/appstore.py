#!/usr/bin/env python3
"""Fills in the App Store listing from appstore/ and submits a build for App Store review.

Uses the ASC_* environment like scripts/asc.py. The review contact comes from APPSTORE_REVIEW_CONTACT
(JSON with firstName, lastName, phone, email) so it never lands in the public repository.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import time
import urllib.request

sys.path.insert(0, str(Path(__file__).resolve().parent))
import asc  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent / 'appstore'
SITE = 'https://seyongida.github.io/lgthinq-climate-widget/'
EDITABLE = {'PREPARE_FOR_SUBMISSION', 'DEVELOPER_REJECTED', 'REJECTED', 'METADATA_REJECTED', 'INVALID_BINARY'}
# Screenshot folder → App Store display type (6.9" iPhone, 13" iPad).
SCREENSHOT_TYPES = {'iphone': 'APP_IPHONE_67', 'ipad': 'APP_IPAD_PRO_3GEN_129'}
AGE_NONE = ['alcoholTobaccoOrDrugUseOrReferences', 'contests', 'gamblingSimulated', 'gunsOrOtherWeapons',
            'horrorOrFearThemes', 'matureOrSuggestiveThemes', 'medicalOrTreatmentInformation', 'profanityOrCrudeHumor',
            'sexualContentGraphicAndNudity', 'sexualContentOrNudity', 'violenceCartoonOrFantasy', 'violenceRealistic',
            'violenceRealisticProlongedGraphicOrSadistic']
AGE_FALSE = ['advertising', 'ageAssurance', 'gambling', 'healthOrWellnessTopics', 'lootBox', 'messagingAndChat',
             'parentalControls', 'socialMedia', 'unrestrictedWebAccess', 'userGeneratedContent']


def log(*parts):
    print(*parts, flush=True)


def optional(client, path, **params):
    """GET that returns None for a missing to-one resource instead of failing."""
    try:
        return client.request('GET', path, params or None).get('data')
    except SystemExit as error:
        if 'HTTP 404' in str(error):
            return None
        raise


def get_all(client, path, **params):
    query = {f'filter[{k[7:]}]' if k.startswith('filter_') else k: v for k, v in params.items()}
    page = client.request('GET', path, query)
    items = list(page['data'])
    while page.get('links', {}).get('next'):
        nxt = page['links']['next']
        page = client.request('GET', nxt[len(client.base):] if nxt.startswith(client.base) else nxt)
        items += page['data']
    return items


def metadata(locale_dir):
    read = lambda name: (locale_dir / name).read_text(encoding='utf-8').strip() if (locale_dir / name).exists() else None
    return {'description': read('description.txt'), 'keywords': read('keywords.txt'),
            'promotionalText': read('promotional_text.txt'), 'subtitle': read('subtitle.txt'),
            'whatsNew': read('release_notes.txt')}


def find_build(client, app, version):
    builds = client.get('/v1/builds', filter_app=app, filter_version=str(version), limit=5)
    if not builds:
        raise SystemExit(f'No uploaded build {version}')
    state = builds[0]['attributes'].get('processingState')
    if state != 'VALID':
        raise SystemExit(f'Build {version} is {state}; wait for processing to finish')
    pre = client.request('GET', f"/v1/builds/{builds[0]['id']}/preReleaseVersion")['data']
    return builds[0]['id'], pre['attributes']['version']


def version_state(attributes):
    return attributes.get('appVersionState') or attributes.get('appStoreState') or ''


def ensure_version(client, app, version_string, build, copyright_text):
    versions = client.get(f'/v1/apps/{app}/appStoreVersions', filter_platform='IOS', limit=20)
    same = [v for v in versions if v['attributes']['versionString'] == version_string]
    editable = [v for v in versions if version_state(v['attributes']) in EDITABLE]
    attributes = {'releaseType': 'AFTER_APPROVAL', 'copyright': copyright_text}
    if same and version_state(same[0]['attributes']) not in EDITABLE:
        raise SystemExit(f"Version {version_string} is already {version_state(same[0]['attributes'])}; raise MARKETING_VERSION for a new release")
    target = same[0] if same else (editable[0] if editable else None)
    if target:
        if target['attributes']['versionString'] != version_string:
            attributes['versionString'] = version_string
        client.request('PATCH', f"/v1/appStoreVersions/{target['id']}", body={'data': {
            'type': 'appStoreVersions', 'id': target['id'], 'attributes': attributes}})
        version = target['id']
    else:
        version = client.request('POST', '/v1/appStoreVersions', body={'data': {
            'type': 'appStoreVersions', 'attributes': {'platform': 'IOS', 'versionString': version_string, **attributes},
            'relationships': {'app': {'data': {'type': 'apps', 'id': app}}}}})['data']['id']
    client.request('PATCH', f'/v1/appStoreVersions/{version}/relationships/build', body={'data': {'type': 'builds', 'id': build}})
    first = len(versions) <= 1 and not any(version_state(v['attributes']) == 'READY_FOR_SALE' for v in versions)
    return version, first


def upsert(client, existing, kind, attributes, relationship, parent):
    """Updates the localization in `existing` or creates it under `parent`."""
    attributes = {k: v for k, v in attributes.items() if v is not None}
    if existing:
        client.request('PATCH', f"/v1/{kind}/{existing['id']}", body={'data': {'type': kind, 'id': existing['id'], 'attributes': attributes}})
        return existing['id']
    return client.request('POST', f'/v1/{kind}', body={'data': {'type': kind, 'attributes': attributes,
                          'relationships': {relationship: {'data': parent}}}})['data']['id']


def editable_app_info(client, app):
    infos = client.get(f'/v1/apps/{app}/appInfos', limit=10)
    for info in infos:
        state = info['attributes'].get('state') or info['attributes'].get('appStoreState') or ''
        if state not in ('READY_FOR_DISTRIBUTION', 'READY_FOR_SALE', 'REPLACED_WITH_NEW_INFO'):
            return info['id']
    return infos[0]['id']


def set_app_info(client, app, locales):
    info = editable_app_info(client, app)
    client.request('PATCH', f'/v1/appInfos/{info}', body={'data': {'type': 'appInfos', 'id': info, 'relationships': {
        'primaryCategory': {'data': {'type': 'appCategories', 'id': 'UTILITIES'}}}}})
    existing = {l['attributes']['locale']: l for l in client.request('GET', f'/v1/appInfos/{info}/appInfoLocalizations')['data']}
    for locale, data in locales.items():
        attributes = {'subtitle': data['subtitle'], 'privacyPolicyUrl': SITE + 'privacy.html'}
        if locale not in existing:
            name = next(iter(existing.values()))['attributes']['name'] if existing else 'Climate Widget for ThinQ'
            attributes = {'locale': locale, 'name': name, **attributes}
        upsert(client, existing.get(locale), 'appInfoLocalizations', attributes, 'appInfo', {'type': 'appInfos', 'id': info})
    declaration = client.request('GET', f'/v1/appInfos/{info}/ageRatingDeclaration')['data']
    present = declaration['attributes']
    answers = {k: 'NONE' for k in AGE_NONE if k in present}
    answers.update({k: False for k in AGE_FALSE if k in present})
    unknown = [k for k, v in present.items() if v is None and k not in answers]
    if unknown:
        log('Age rating fields left unanswered:', ', '.join(unknown))
    client.request('PATCH', f"/v1/ageRatingDeclarations/{declaration['id']}", body={'data': {
        'type': 'ageRatingDeclarations', 'id': declaration['id'], 'attributes': answers}})
    log(f'App info: Utilities, age rating answered ({len(answers)} questions), {", ".join(locales)}')


def set_free_everywhere(client, app):
    if optional(client, f'/v1/apps/{app}/appPriceSchedule'):
        log('Price already set')
    else:
        points = get_all(client, f'/v1/apps/{app}/appPricePoints', filter_territory='KOR', limit=200)
        free = next(p for p in points if float(p['attributes'].get('customerPrice') or 0) == 0)
        client.request('POST', '/v1/appPriceSchedules', body={
            'data': {'type': 'appPriceSchedules', 'relationships': {
                'app': {'data': {'type': 'apps', 'id': app}},
                'baseTerritory': {'data': {'type': 'territories', 'id': 'KOR'}},
                'manualPrices': {'data': [{'type': 'appPrices', 'id': '${free}'}]}}},
            'included': [{'type': 'appPrices', 'id': '${free}', 'attributes': {'startDate': None},
                          'relationships': {'appPricePoint': {'data': {'type': 'appPricePoints', 'id': free['id']}}}}]})
        log('Price: free')
    if optional(client, f'/v1/apps/{app}/appAvailabilityV2'):
        log('Availability already set')
        return
    territories = [t['id'] for t in get_all(client, '/v1/territories', limit=200)]
    client.request('POST', '/v2/appAvailabilities', body={
        'data': {'type': 'appAvailabilities', 'attributes': {'availableInNewTerritories': True}, 'relationships': {
            'app': {'data': {'type': 'apps', 'id': app}},
            'territoryAvailabilities': {'data': [{'type': 'territoryAvailabilities', 'id': '${%s}' % t} for t in territories]}}},
        'included': [{'type': 'territoryAvailabilities', 'id': '${%s}' % t, 'attributes': {'available': True},
                      'relationships': {'territory': {'data': {'type': 'territories', 'id': t}}}} for t in territories]})
    log(f'Availability: {len(territories)} territories')


def upload_screenshots(client, localization, folder, display_type, wait_seconds=600):
    files = sorted(folder.glob('*.png'))
    if not files:
        return 0
    sets = client.request('GET', f'/v1/appStoreVersionLocalizations/{localization}/appScreenshotSets')['data']
    found = [s for s in sets if s['attributes']['screenshotDisplayType'] == display_type]
    if found:
        screenshot_set = found[0]['id']
        for old in client.request('GET', f'/v1/appScreenshotSets/{screenshot_set}/appScreenshots')['data']:
            client.request('DELETE', f"/v1/appScreenshots/{old['id']}")
    else:
        screenshot_set = client.request('POST', '/v1/appScreenshotSets', body={'data': {
            'type': 'appScreenshotSets', 'attributes': {'screenshotDisplayType': display_type},
            'relationships': {'appStoreVersionLocalization': {'data': {'type': 'appStoreVersionLocalizations', 'id': localization}}}}})['data']['id']
    ids = []
    for path in files:
        data = path.read_bytes()
        shot = client.request('POST', '/v1/appScreenshots', body={'data': {
            'type': 'appScreenshots', 'attributes': {'fileName': path.name, 'fileSize': len(data)},
            'relationships': {'appScreenshotSet': {'data': {'type': 'appScreenshotSets', 'id': screenshot_set}}}}})['data']
        for op in shot['attributes']['uploadOperations']:
            part = urllib.request.Request(op['url'], method=op['method'], data=data[op['offset']:op['offset'] + op['length']])
            for header in op.get('requestHeaders') or []:
                part.add_header(header['name'], header['value'])
            urllib.request.urlopen(part, timeout=120).read()
        client.request('PATCH', f"/v1/appScreenshots/{shot['id']}", body={'data': {
            'type': 'appScreenshots', 'id': shot['id'],
            'attributes': {'uploaded': True, 'sourceFileChecksum': hashlib.md5(data).hexdigest()}}})
        ids.append(shot['id'])
    deadline = time.time() + wait_seconds
    for shot in ids:
        while True:
            state = client.request('GET', f'/v1/appScreenshots/{shot}')['data']['attributes'].get('assetDeliveryState') or {}
            if state.get('state') == 'COMPLETE':
                break
            if state.get('state') == 'FAILED':
                raise SystemExit(f'Screenshot processing failed: {state.get("errors")}')
            if time.time() > deadline:
                raise SystemExit('Screenshots are still processing; run again later')
            time.sleep(5)
    return len(ids)


def set_review_details(client, version, contact, notes):
    attributes = {'contactFirstName': contact['firstName'], 'contactLastName': contact['lastName'],
                  'contactPhone': contact['phone'], 'contactEmail': contact['email'],
                  'demoAccountRequired': False, 'notes': notes}
    existing = optional(client, f'/v1/appStoreVersions/{version}/appStoreReviewDetail')
    upsert(client, existing, 'appStoreReviewDetails', attributes, 'appStoreVersion', {'type': 'appStoreVersions', 'id': version})


def submit_for_review(client, app, version):
    open_states = 'READY_FOR_REVIEW,WAITING_FOR_REVIEW,IN_REVIEW,UNRESOLVED_ISSUES'
    current = client.get('/v1/reviewSubmissions', filter_app=app, filter_platform='IOS', filter_state=open_states, limit=5)
    for submission in current:
        if submission['attributes']['state'] in ('WAITING_FOR_REVIEW', 'IN_REVIEW'):
            return 'already ' + submission['attributes']['state']
    if current:
        submission = current[0]['id']
    else:
        submission = client.request('POST', '/v1/reviewSubmissions', body={'data': {
            'type': 'reviewSubmissions', 'attributes': {'platform': 'IOS'},
            'relationships': {'app': {'data': {'type': 'apps', 'id': app}}}}})['data']['id']
    items = client.request('GET', f'/v1/reviewSubmissions/{submission}/items')['data']
    if not items:
        client.request('POST', '/v1/reviewSubmissionItems', body={'data': {'type': 'reviewSubmissionItems', 'relationships': {
            'reviewSubmission': {'data': {'type': 'reviewSubmissions', 'id': submission}},
            'appStoreVersion': {'data': {'type': 'appStoreVersions', 'id': version}}}}})
    result = client.request('PATCH', f'/v1/reviewSubmissions/{submission}', body={'data': {
        'type': 'reviewSubmissions', 'id': submission, 'attributes': {'submitted': True}}})
    return result['data']['attributes'].get('state', 'submitted')


def latest_valid_build(client, app):
    for build in client.get('/v1/builds', filter_app=app, sort='-uploadedDate', limit=10):
        if build['attributes'].get('processingState') == 'VALID' and not build['attributes'].get('expired'):
            return build['attributes']['version']
    raise SystemExit('No processed build to submit')


def store_state(client, bundle_id):
    """(state, version, app id) of the newest App Store version, or ('NONE', '', app id)."""
    app = client.get('/v1/apps', filter_bundleId=bundle_id, limit=1)[0]['id']
    versions = client.get(f'/v1/apps/{app}/appStoreVersions', filter_platform='IOS', limit=20)
    if not versions:
        return 'NONE', '', app
    newest = max(versions, key=lambda v: v['attributes'].get('createdDate') or '')
    return version_state(newest['attributes']), newest['attributes']['versionString'], app


def publish(client, bundle_id, build_number, contact, submit=True, screenshots=True, notes=''):
    app = client.get('/v1/apps', filter_bundleId=bundle_id, limit=1)[0]['id']
    build_number = build_number or latest_valid_build(client, app)
    build, version_string = find_build(client, app, build_number)
    primary = client.request('GET', f'/v1/apps/{app}')['data']['attributes'].get('primaryLocale', 'ko')
    locales = {d.name: metadata(d) for d in sorted((ROOT / 'metadata').iterdir()) if d.is_dir()}
    if notes and 'ko' in locales:
        locales['ko']['whatsNew'] = notes
    if primary not in locales:
        raise SystemExit(f'appstore/metadata has no folder for the primary language {primary}')
    client.request('PATCH', f'/v1/apps/{app}', body={'data': {'type': 'apps', 'id': app,
                   'attributes': {'contentRightsDeclaration': 'USES_THIRD_PARTY_CONTENT'}}})
    set_app_info(client, app, locales)
    set_free_everywhere(client, app)
    year = time.strftime('%Y')
    version, first = ensure_version(client, app, version_string, build, f"{year} {contact['lastName']}{contact['firstName']}")
    log(f'Version {version_string} with build {build_number} (release after approval)')
    existing = {l['attributes']['locale']: l for l in
                client.request('GET', f'/v1/appStoreVersions/{version}/appStoreVersionLocalizations')['data']}
    localized = {}
    for locale, data in locales.items():
        attributes = {'description': data['description'], 'keywords': data['keywords'], 'promotionalText': data['promotionalText'],
                      'supportUrl': SITE, 'marketingUrl': SITE, 'whatsNew': None if first else data['whatsNew']}
        if locale not in existing:
            attributes['locale'] = locale
        localized[locale] = upsert(client, existing.get(locale), 'appStoreVersionLocalizations', attributes,
                                   'appStoreVersion', {'type': 'appStoreVersions', 'id': version})
    log('Store text:', ', '.join(localized))
    if screenshots:
        for folder, display_type in SCREENSHOT_TYPES.items():
            count = upload_screenshots(client, localized[primary], ROOT / 'screenshots' / folder, display_type)
            log(f'Screenshots {folder}: {count}')
    set_review_details(client, version, contact, (ROOT / 'review_notes.txt').read_text(encoding='utf-8').strip())
    log('Review contact and notes set')
    if submit:
        log('Submitted for App Store review:', submit_for_review(client, app, version))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    run = commands.add_parser('publish', help='Fill in the listing and submit a build for App Store review')
    run.add_argument('--build', default='', help='Build number; defaults to the newest processed build')
    run.add_argument('--no-submit', action='store_true', help='Only fill in the listing')
    run.add_argument('--no-screenshots', action='store_true')
    run.add_argument('--notes', default='', help='Korean "What\'s New" text for an update')
    commands.add_parser('state', help='Print the newest App Store version state and version')
    args = parser.parse_args()
    bundle = os.environ.get('IOS_BUNDLE_ID', 'dev.thinqclimate.widget')
    if args.command == 'state':
        print(*store_state(asc.client_from_env(), bundle))
        return
    raw = os.environ.get('APPSTORE_REVIEW_CONTACT', '')
    try:
        contact = json.loads(raw)
        assert all(contact.get(k) for k in ('firstName', 'lastName', 'phone', 'email'))
    except (ValueError, AssertionError, AttributeError):
        raise SystemExit('APPSTORE_REVIEW_CONTACT must be JSON with firstName, lastName, phone and email')
    publish(asc.client_from_env(), bundle, args.build, contact, not args.no_submit, not args.no_screenshots, args.notes)


if __name__ == '__main__':
    main()
