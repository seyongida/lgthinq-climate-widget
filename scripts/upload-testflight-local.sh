#!/usr/bin/env bash
# Archive with the Apple account signed in to Xcode, then upload to App Store Connect.
# Usage: scripts/upload-testflight-local.sh <build-number> [--dry-run]
# --dry-run signs and exports an IPA locally without uploading it.
set -euo pipefail
cd "$(dirname "$0")/.."

build_number=${1:-}
[[ "$build_number" =~ ^[1-9][0-9]{0,3}(\.[0-9]{1,2}){0,2}$ ]] || { echo "Usage: $0 <build-number> [--dry-run]" >&2; exit 2; }
destination=upload
[[ "${2:-}" == "--dry-run" ]] && destination=export

team=${IOS_TEAM_ID:-$(gh variable get IOS_TEAM_ID)}
xcode_major=$(xcodebuild -version | sed -n 's/^Xcode \([0-9]*\).*/\1/p')
(( xcode_major >= 26 )) || { echo "App Store Connect requires Xcode 26 or later" >&2; exit 1; }

work=$(mktemp -d "${TMPDIR:-/tmp}/thinq-testflight.XXXXXX")
trap 'rm -rf "$work"' EXIT
cat > "$work/ExportOptions.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>method</key><string>app-store-connect</string>
  <key>destination</key><string>$destination</string>
  <key>signingStyle</key><string>automatic</string>
  <key>teamID</key><string>$team</string>
  <key>manageAppVersionAndBuildNumber</key><false/>
  <key>uploadSymbols</key><true/>
</dict></plist>
PLIST

swift test --package-path ios
xcodegen generate --spec ios/project.yml
xcodebuild -project ios/ThinQClimate.xcodeproj -scheme ThinQClimate -configuration Release \
  -destination 'generic/platform=iOS' -archivePath "$work/ThinQClimate.xcarchive" -allowProvisioningUpdates \
  DEVELOPMENT_TEAM="$team" CURRENT_PROJECT_VERSION="$build_number" CODE_SIGN_STYLE=Automatic \
  CODE_SIGN_IDENTITY="Apple Development" PROVISIONING_PROFILE_SPECIFIER= archive
xcodebuild -exportArchive -archivePath "$work/ThinQClimate.xcarchive" -allowProvisioningUpdates \
  -exportOptionsPlist "$work/ExportOptions.plist" -exportPath "$work/export"

if [[ "$destination" == upload ]]; then
  echo "Uploaded build $build_number. Continue in App Store Connect once Apple finishes processing it."
else
  ls -l "$work/export"
  echo "Dry run succeeded for build $build_number. Nothing was uploaded."
fi
