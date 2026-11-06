#!/usr/bin/env python3
import os
import re
import sys
version = sys.argv[1]
repo = os.environ.get('GH_REPO', 'seyongida/lgthinq-climate-widget')
url = f'https://github.com/{repo}/releases/download/{version}/ThinQ-Climate.apk'
print(f'''# ThinQ Climate {version}

## Android

[Download ThinQ-Climate.apk]({url})

Open the APK on Android 8 or later and allow installation from your browser when Android asks.
On first launch, enter your own LG ThinQ Personal Access Token. You can replace it anytime in Connection settings.
The public app contains no developer PAT. Public and personal Dev apps can be installed separately.
SHA256SUMS and SIGNING_CERTIFICATE.txt are provided with the APK.

## iPhone / iPad
''')
link = os.environ.get('IOS_TESTFLIGHT_URL', '').strip()
if re.fullmatch(r'https://testflight\.apple\.com/join/[A-Za-z0-9]+', link):
    print(f'[Install through TestFlight]({link})\n\nInstall Apple TestFlight first, then open the invitation link. Enter your own PAT after installation; it can be changed in the app.')
else:
    print('iOS public installation is not available yet. The TestFlight workflow is prepared, but Apple Developer signing, App Store Connect setup and a public invitation link must be configured. An unsigned IPA or simulator ZIP cannot be installed directly on an ordinary iPhone.')
print('\nLG ThinQ is a trademark of its owner. This is an independent, read-only client, not an official LG app.')
