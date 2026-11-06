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
if marker not in html:
    raise SystemExit('Install marker is missing from site/index.html')
page.write_text(html.replace(marker, block), encoding='utf-8')
print(f'Built {target} (iOS link: {"set" if link else "pending"})')
