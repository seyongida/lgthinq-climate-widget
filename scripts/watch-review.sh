#!/usr/bin/env bash
# Opens a GitHub issue when a TestFlight review finishes, so the owner is emailed, and starts the
# Firebase fallback once if the review was rejected.
set -euo pipefail

if [[ -z "${ASC_API_KEY_P8_BASE64:-}" ]]; then
  echo "App Store Connect API key is not configured. Nothing to watch."
  exit 0
fi

read -r state build < <(python3 scripts/asc.py review-latest)
echo "Newest TestFlight review state: $state (build ${build:-none})"

notify() {
  local title=$1 body=$2
  if gh issue list --state all --limit 100 --json title --jq '.[].title' | grep -qxF "$title"; then
    echo "Already notified: $title"
  else
    gh issue create --title "$title" --body "$body" >/dev/null
    echo "Notified: $title"
  fi
}
owner="@${OWNER:-}"
link="${IOS_TESTFLIGHT_URL:-}"
case "$state" in
  APPROVED)
    notify "TestFlight 빌드 $build 승인됨" "$owner TestFlight 빌드 $build의 베타 심사가 승인됐어요.

공개 링크는 그대로이며, 이제 이 링크로 새 빌드를 받을 수 있어요: ${link:-App Store Connect의 TestFlight 공개 링크}
이미 설치한 사람은 TestFlight 앱에서 업데이트를 누르면 됩니다.

이 이슈는 알림용이라 닫아도 됩니다."
    exit 0 ;;
  REJECTED)
    notify "TestFlight 빌드 $build 거절됨" "$owner TestFlight 빌드 $build의 베타 심사가 거절됐어요.

거절 사유는 App Store Connect → 앱 → TestFlight → 왼쪽 General의 App Review에서 확인할 수 있어요.
Firebase 대체 배포는 설정돼 있으면 자동으로 한 번 시작됩니다. Actions의 Distribute iOS build with Firebase에서 결과를 확인하세요." ;;
  *) exit 0 ;;
esac

if gh run list --workflow ios-adhoc.yml --limit 20 --json createdAt --jq '.[].createdAt' | grep -q .; then
  echo "A Firebase build was already started."
  exit 0
fi
if [[ "${HAS_DEVICES:-}" != "true" || "${HAS_CERTIFICATE:-}" != "true" || "${HAS_FIREBASE:-}" != "true" ]]; then
  # A failed run makes GitHub email the repository owner.
  echo "::error::TestFlight review was rejected, but Firebase distribution is not configured. See docs/DISTRIBUTION.md."
  exit 1
fi
gh workflow run ios-adhoc.yml --ref main
echo "Started the Firebase fallback."
