#!/usr/bin/env bash
# Build a device-limited Ad Hoc IPA and its install manifest in the given directory.
# Requires the secrets saved by `scripts/asc.py setup`. Runs on GitHub Actions macOS runners.
set -euo pipefail
output=$1
site_url=${SITE_URL:-https://seyongida.github.io/lgthinq-climate-widget}

xcode_major=$(xcodebuild -version | sed -n 's/^Xcode \([0-9]*\).*/\1/p')
(( xcode_major >= 26 )) || { echo "Xcode 26 or later is required" >&2; exit 1; }

export IOS_SIGNING_DIR
IOS_SIGNING_DIR=$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/thinq-adhoc.XXXXXX")
keychain="$IOS_SIGNING_DIR/adhoc.keychain-db"
keychain_password=$(openssl rand -hex 24)
original_keychains=$(security list-keychains -d user | tr -d '"')
cleanup() {
  # shellcheck disable=SC2086
  security list-keychains -d user -s $original_keychains
  security delete-keychain "$keychain" >/dev/null 2>&1 || true
  if [[ -f "$IOS_SIGNING_DIR/profiles.json" ]]; then
    python3 -c 'import json,sys,pathlib; [pathlib.Path(p).unlink(missing_ok=True) for r in json.load(open(sys.argv[1])).values() for p in r["paths"]]' "$IOS_SIGNING_DIR/profiles.json"
  fi
  rm -rf "$IOS_SIGNING_DIR"
}
trap cleanup EXIT

python3 scripts/asc.py prepare-adhoc

printf '%s' "$IOS_DISTRIBUTION_P12_BASE64" | base64 --decode > "$IOS_SIGNING_DIR/distribution.p12"
chmod 600 "$IOS_SIGNING_DIR/distribution.p12"
security create-keychain -p "$keychain_password" "$keychain"
security set-keychain-settings -lut 21600 "$keychain"
security unlock-keychain -p "$keychain_password" "$keychain"
# shellcheck disable=SC2086
security list-keychains -d user -s "$keychain" $original_keychains
security import "$IOS_SIGNING_DIR/distribution.p12" -P "$IOS_DISTRIBUTION_P12_PASSWORD" -T /usr/bin/codesign -T /usr/bin/security -t cert -f pkcs12 -k "$keychain"
security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$keychain_password" "$keychain" >/dev/null

profile_name() { python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[sys.argv[2]]["name"])' "$IOS_SIGNING_DIR/profiles.json" "$1"; }

xcodegen generate --spec ios/project.yml
xcodebuild -project ios/ThinQClimate.xcodeproj -scheme ThinQClimate -configuration Release \
  -destination 'generic/platform=iOS' -archivePath "$IOS_SIGNING_DIR/ThinQClimate.xcarchive" \
  DEVELOPMENT_TEAM="$IOS_TEAM_ID" CLIMATE_BUNDLE_ID="$IOS_BUNDLE_ID" CLIMATE_APP_GROUP="$IOS_APP_GROUP" \
  CURRENT_PROJECT_VERSION="$IOS_BUILD_NUMBER" CLIMATE_APP_PROFILE="$(profile_name app)" \
  CLIMATE_WIDGET_PROFILE="$(profile_name widget)" OTHER_CODE_SIGN_FLAGS="--keychain $keychain" archive
xcodebuild -exportArchive -archivePath "$IOS_SIGNING_DIR/ThinQClimate.xcarchive" \
  -exportOptionsPlist "$IOS_SIGNING_DIR/ExportOptions.plist" -exportPath "$IOS_SIGNING_DIR/export"

mkdir -p "$output"
cp "$IOS_SIGNING_DIR/export/ThinQClimate.ipa" "$output/ThinQClimate.ipa"
version=$(/usr/libexec/PlistBuddy -c 'Print :ApplicationProperties:CFBundleShortVersionString' "$IOS_SIGNING_DIR/ThinQClimate.xcarchive/Info.plist")
python3 scripts/asc.py manifest --ipa-url "$site_url/adhoc/ThinQClimate.ipa" --bundle-id "$IOS_BUNDLE_ID" \
  --bundle-version "$version" --title "Climate Widget" --out "$output/manifest.plist"
echo "Built Ad Hoc IPA and manifest"
