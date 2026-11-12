#!/usr/bin/env bash
# Uploads a fresh TestFlight build before the current one expires, and warns before signing expires.
set -euo pipefail
RENEW_WITHIN_DAYS=${RENEW_WITHIN_DAYS:-20}
SIGNING_WARN_DAYS=${SIGNING_WARN_DAYS:-30}

days=$(python3 scripts/asc.py testflight-days)
echo "The newest TestFlight build for external testers expires in $days day(s)."
if (( days <= RENEW_WITHIN_DAYS )); then
  if gh run list --workflow ios-testflight.yml --status in_progress --json databaseId --jq '.[].databaseId' | grep -q .; then
    echo "An upload is already running."
  else
    gh workflow run ios-testflight.yml --ref main -f notes="앱을 계속 쓸 수 있도록 새 빌드로 교체했어요. 기능은 이전과 같습니다."
    echo "Started a fresh TestFlight upload."
  fi
fi

signing=$(python3 scripts/asc.py signing-days)
echo "The distribution certificate and App Store profiles expire in $signing day(s)."
if (( signing <= SIGNING_WARN_DAYS )); then
  # A failed run makes GitHub email the repository owner.
  echo "::error::Signing expires in $signing day(s). Renew it with scripts/asc.py setup and appstore-profiles (docs/DISTRIBUTION.md)."
  exit 1
fi
