#!/usr/bin/env bash
# Captures App Store screenshots from demo mode on the iPhone 6.9" and iPad 13" simulators.
# Output: appstore/screenshots/{iphone,ipad}/N-name.png
set -euo pipefail
cd "$(dirname "$0")/.."
work=$(mktemp -d "${TMPDIR:-/tmp}/store-shots.XXXXXX")
trap 'rm -rf "$work"' EXIT
xcodegen generate --spec ios/project.yml >/dev/null

capture() {
  local kind=$1 name=$2
  local sim
  sim=$(xcrun simctl list devices available | sed -n "s/^ *$name (\([0-9A-F-]*\)).*/\1/p" | head -1)
  [[ -n "$sim" ]] || { echo "No simulator named $name" >&2; exit 1; }
  xcrun simctl boot "$sim" 2>/dev/null || true
  xcrun simctl bootstatus "$sim" -b >/dev/null
  # Korean system UI so the Lock Screen and permission prompts match the app.
  xcrun simctl spawn "$sim" defaults write -g AppleLanguages -array ko
  xcrun simctl spawn "$sim" defaults write -g AppleLocale ko_KR
  xcrun simctl shutdown "$sim"; xcrun simctl boot "$sim"; xcrun simctl bootstatus "$sim" -b >/dev/null
  xcrun simctl keychain "$sim" reset
  xcrun simctl uninstall "$sim" dev.thinqclimate.widget 2>/dev/null || true
  xcrun simctl status_bar "$sim" override --time 9:41 --batteryState charged --batteryLevel 100 \
    --operatorName "" --cellularMode active --cellularBars 4 --wifiBars 3 --dataNetwork wifi
  TEST_RUNNER_STORE_SHOTS=1 xcodebuild test -project ios/ThinQClimate.xcodeproj -scheme ThinQClimate \
    -destination "id=$sim" -derivedDataPath "$work/dd" -resultBundlePath "$work/$kind.xcresult" \
    -only-testing:ThinQClimateUITests/DemoModeUITests/testStoreScreenshots CODE_SIGNING_ALLOWED=YES -quiet
  xcrun xcresulttool export attachments --path "$work/$kind.xcresult" --output-path "$work/$kind"
  local out="appstore/screenshots/$kind"
  rm -rf "$out"; mkdir -p "$out"
  python3 - "$work/$kind" "$out" <<'PY'
import json, shutil, sys
from pathlib import Path
src, out = Path(sys.argv[1]), Path(sys.argv[2])
for test in json.loads((src / 'manifest.json').read_text()):
    for item in test['attachments']:
        name = item['suggestedHumanReadableName'].split('_')[0]
        if name.startswith('store-'):
            shutil.copy(src / item['exportedFileName'], out / (name[6:] + '.png'))
PY
  xcrun simctl status_bar "$sim" clear
  ls "$out"
}

capture iphone "iPhone 17 Pro Max"
capture ipad "iPad Pro 13-inch (M5)"
