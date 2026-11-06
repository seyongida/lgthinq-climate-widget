#!/usr/bin/env bash
# Start the Ad Hoc fallback once when the newest TestFlight review is rejected.
set -euo pipefail

if [[ -z "${ASC_API_KEY_P8_BASE64:-}" ]]; then
  echo "App Store Connect API key is not configured. Nothing to watch."
  exit 0
fi

state=$(python3 scripts/asc.py review-state)
echo "Newest TestFlight review state: $state"
[[ "$state" == "REJECTED" ]] || exit 0

if gh release list --limit 50 --json tagName --jq '.[].tagName' | grep -q '^adhoc-'; then
  echo "An Ad Hoc release already exists."
  exit 0
fi
if [[ "${HAS_DEVICES:-}" != "true" || "${HAS_CERTIFICATE:-}" != "true" ]]; then
  # A failed run makes GitHub email the repository owner.
  echo "::error::TestFlight review was rejected, but no Ad Hoc device or certificate is configured. Run scripts/asc.py setup with --device."
  exit 1
fi
gh workflow run ios-adhoc.yml --ref main
echo "Started the Ad Hoc fallback."
