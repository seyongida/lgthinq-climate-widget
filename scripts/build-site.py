#!/usr/bin/env python3
"""Build the install page. Shows the iOS button only for a real TestFlight invitation link."""
import os
from pathlib import Path
import re
import shutil
import sys

source = Path(__file__).resolve().parent.parent / 'site'
target = Path(sys.argv[1])
link = os.environ.get('IOS_TESTFLIGHT_URL', '').strip()
marker = '<!-- ios-install -->'
adhoc_marker = '<!-- adhoc-install -->'

if not link:
    block = '<p class="pending">TestFlight 공개 링크 준비 중입니다.</p>'
elif re.fullmatch(r'https://testflight\.apple\.com/join/[A-Za-z0-9]+', link):
    block = f'<a class="button" href="{link}">TestFlight로 설치</a>'
else:
    raise SystemExit('IOS_TESTFLIGHT_URL is not a TestFlight invitation link')

shutil.rmtree(target, ignore_errors=True)
shutil.copytree(source, target)
page = target / 'index.html'
html = page.read_text(encoding='utf-8')
if marker not in html or adhoc_marker not in html:
    raise SystemExit('Install marker is missing from site/index.html')

adhoc_block = ''
adhoc_dir = os.environ.get('ADHOC_DIR', '').strip()
if adhoc_dir:
    site_url = os.environ.get('SITE_URL', 'https://seyongida.github.io/lgthinq-climate-widget')
    published = target / 'adhoc'
    published.mkdir()
    for name in ['ThinQClimate.ipa', 'manifest.plist']:
        shutil.copy(Path(adhoc_dir) / name, published / name)
    adhoc_block = f'''<div class="adhoc">
      <h3>등록된 기기에 직접 설치</h3>
      <p class="note">개발자에게 기기를 등록한 분만 설치할 수 있으며, 다른 기기에서는 설치가 실패합니다. iPhone의 Safari에서 누른 뒤 설정 → 개인정보 보호 및 보안 → 개발자 모드를 켜야 할 수 있습니다.</p>
      <a class="button" href="itms-services://?action=download-manifest&amp;url={site_url}/adhoc/manifest.plist">직접 설치</a>
    </div>'''

page.write_text(html.replace(marker, block).replace(adhoc_marker, adhoc_block), encoding='utf-8')
print(f'Built {target} (iOS link: {"set" if link else "pending"}, Ad Hoc: {"set" if adhoc_dir else "none"})')
