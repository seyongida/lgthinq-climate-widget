#!/usr/bin/env bash
set -euo pipefail
python3 scripts/ios-signing.py --check
xcode_major=$(xcodebuild -version | sed -n 's/^Xcode \([0-9]*\).*/\1/p')
(( xcode_major >= 26 )) || { echo "App Store Connect requires Xcode 26 or later" >&2; exit 1; }
export IOS_SIGNING_DIR
IOS_SIGNING_DIR=$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/thinq-signing.XXXXXX")
keychain_path="$IOS_SIGNING_DIR/distribution.keychain-db"
keychain_password=$(openssl rand -hex 24)
cleanup() {
  if [[ -f "$IOS_SIGNING_DIR/profiles.json" ]]; then
    python3 - <<'PY'
import json, os
from pathlib import Path
for profile in json.loads((Path(os.environ['IOS_SIGNING_DIR']) / 'profiles.json').read_text()).values():
    Path(profile['path']).unlink(missing_ok=True)
PY
  fi
  if [[ -f "$IOS_SIGNING_DIR/search-list.json" ]]; then
    python3 - <<'PYRESTORE'
import json, os, subprocess
from pathlib import Path
items=json.loads((Path(os.environ['IOS_SIGNING_DIR'])/'search-list.json').read_text())
subprocess.run(['security','list-keychains','-d','user','-s',*items],check=True)
PYRESTORE
  fi
  security delete-keychain "$keychain_path" >/dev/null 2>&1 || true
  rm -rf "$IOS_SIGNING_DIR"
}
trap cleanup EXIT
python3 scripts/ios-signing.py
security create-keychain -p "$keychain_password" "$keychain_path"
security set-keychain-settings -lut 21600 "$keychain_path"
security unlock-keychain -p "$keychain_password" "$keychain_path"
python3 - <<'PYSEARCH'
import json, os, shlex, subprocess
from pathlib import Path
root=Path(os.environ['IOS_SIGNING_DIR'])
items=shlex.split(subprocess.check_output(['security','list-keychains','-d','user'],text=True))
(root/'search-list.json').write_text(json.dumps(items))
subprocess.run(['security','list-keychains','-d','user','-s',str(root/'distribution.keychain-db'),*items],check=True)
PYSEARCH
security import "$IOS_SIGNING_DIR/distribution.p12" -P "${IOS_DISTRIBUTION_P12_PASSWORD:-}" -T /usr/bin/codesign -T /usr/bin/security -t cert -f pkcs12 -k "$keychain_path"
security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$keychain_password" "$keychain_path" >/dev/null
app_profile=$(python3 -c 'import json,os; from pathlib import Path; print(json.loads((Path(os.environ["IOS_SIGNING_DIR"])/"profiles.json").read_text())["app"]["name"])')
widget_profile=$(python3 -c 'import json,os; from pathlib import Path; print(json.loads((Path(os.environ["IOS_SIGNING_DIR"])/"profiles.json").read_text())["widget"]["name"])')
xcodegen generate --spec ios/project.yml
xcodebuild -project ios/ThinQClimate.xcodeproj -scheme ThinQClimate -configuration Release \
  -destination 'generic/platform=iOS' -archivePath "$IOS_SIGNING_DIR/ThinQClimate.xcarchive" \
  DEVELOPMENT_TEAM="$IOS_TEAM_ID" CLIMATE_BUNDLE_ID="$IOS_BUNDLE_ID" CLIMATE_APP_GROUP="$IOS_APP_GROUP" \
  CURRENT_PROJECT_VERSION="$IOS_BUILD_NUMBER" CLIMATE_APP_PROFILE="$app_profile" CLIMATE_WIDGET_PROFILE="$widget_profile" \
  OTHER_CODE_SIGN_FLAGS="--keychain $keychain_path" archive
xcodebuild -exportArchive -archivePath "$IOS_SIGNING_DIR/ThinQClimate.xcarchive" \
  -exportOptionsPlist "$IOS_SIGNING_DIR/ExportOptions.plist" -exportPath "$IOS_SIGNING_DIR/export" \
  -authenticationKeyPath "$IOS_SIGNING_DIR/AuthKey.p8" -authenticationKeyID "$ASC_API_KEY_ID" \
  -authenticationKeyIssuerID "$ASC_API_ISSUER_ID"
echo 'Uploaded to App Store Connect. Apple processing and TestFlight review/public-link setup remain in App Store Connect.'
