# GitHub 다운로드 배포

## 사용자 다운로드 주소

- Android: https://github.com/seyongida/lgthinq-climate-widget/releases/latest/download/ThinQ-Climate.apk
- 버전별 파일/체크섬: https://github.com/seyongida/lgthinq-climate-widget/releases
- iOS: TestFlight 서명/심사/공개 초대 링크 설정 후 릴리즈 설명과 설치 페이지에 연결합니다. 현재 실제 공개 초대 링크는 등록하지 않았습니다.
- 설치 페이지: GitHub Pages로 `site/`를 게시합니다. Settings → Pages의 Source를 **GitHub Actions**로 설정해야 합니다. `https://seyongida.github.io/lgthinq-climate-widget/`
- 개인정보 처리방침: `https://seyongida.github.io/lgthinq-climate-widget/privacy.html`

공개 Android APK는 `.env`가 있는 개발 PC에서 만들어도 PAT가 항상 비어 있습니다. 첫 실행 시 사용자 PAT를 입력받고 연결 설정에서 언제든 교체합니다. 사용자 입력은 Keystore/Keychain에 저장합니다. 토큰 교체가 확인되면 이전 센서 캐시를 비우고 늦은 응답을 거부합니다. 검증 실패 시 기존 인증은 유지합니다.

## Android 서명과 GitHub Secrets

최초 배포용 키는 로컬 `android/keystore/release.jks`, 서명 설정은 루트 `.env.signing`에 보관합니다. 두 파일 모두 Git에서 제외하며 파일 권한은 600입니다. **이 키와 암호를 별도로 백업해야 같은 앱을 계속 업데이트할 수 있습니다.** 키를 새로 만들면 기존 사용자의 앱을 같은 서명으로 업데이트할 수 없습니다.

GitHub Actions 저장소 Secrets:

| Secret | 값 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | 배포 keystore 파일의 base64 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 암호 |
| `ANDROID_KEY_ALIAS` | 키 alias |
| `ANDROID_KEY_PASSWORD` | 해당 키 암호 |

루트 `.env.signing`은 위 암호/alias와 `ANDROID_KEYSTORE_FILE=keystore/release.jks`를 사용합니다. CI에서는 같은 이름의 환경 변수로 서명을 주입하고 작업 종료 시 임시 키를 삭제합니다. **LG PAT는 GitHub Secrets나 배포 워크플로에 전달하지 않습니다.**

버전 번호를 `android/app/build.gradle.kts`와 `ios/project.yml`에서 변경하고 검사한 뒤 태그를 게시합니다.

```sh
git tag v1.5.0
git push origin v1.5.0
```

`Publish Android release`는 코어/Release 단위 테스트/lint → APK 빌드 → 공개 자격 증명/버전/서명 검사 → GitHub Releases 게시 순서로 실행합니다. 게시 파일은 `ThinQ-Climate.apk`, `SHA256SUMS`, `SIGNING_CERTIFICATE.txt`, `INSTALL.md`입니다. 같은 릴리즈를 덮어쓰지 않으며 수정은 새 버전 태그로 배포합니다.

`Mobile CI`의 Debug APK는 검사/개발용이며, 일반 사용자는 Releases의 공개 APK를 사용합니다. CI에는 `.env`가 없으므로 Debug 산출물에도 개발자 토큰이 없습니다.

## iOS 배포 준비

일반 iPhone에 APK처럼 IPA 파일만 다운로드해 설치시키는 방식은 사용할 수 없습니다. 이 프로젝트의 공개 배포 경로는 **TestFlight 공개 초대 링크**입니다. Apple Developer Program, App Store Connect 앱 등록, 두 Bundle ID와 App Group, App Store 배포 인증서와 두 프로비저닝 프로파일이 필요합니다. 외부 테스트는 Apple의 TestFlight 심사와 공개 그룹/초대 링크 설정도 필요합니다.

서명되지 않은 IPA는 설치 파일로 공개하지 않습니다. App Store Connect는 Xcode 26 이상으로 만든 빌드만 받습니다.

### 공개 링크 만들기

1. [App Store Connect](https://appstoreconnect.apple.com/apps)에서 새 앱을 만들고 Bundle ID `dev.thinqclimate.widget`을 선택합니다. 앱 이름은 LG 상표 문제를 피하도록 정합니다.
2. Xcode에 Apple 계정이 로그인된 Mac에서 업로드합니다. `--dry-run`을 붙이면 업로드 없이 서명과 내보내기만 확인합니다. 빌드 번호는 이전보다 커야 합니다.

   ```sh
   scripts/upload-testflight-local.sh <build-number>
   ```

3. TestFlight 탭의 테스트 정보에 앱 설명, 피드백 이메일, 위 개인정보 처리방침 주소, 아래 심사 메모를 입력합니다.
4. 외부 테스트 그룹을 만들고 빌드를 추가해 베타 심사에 제출합니다. 승인 후 그룹의 공개 링크를 켭니다.
5. 링크를 등록하고 설치 페이지를 다시 게시합니다.

   ```sh
   gh variable set IOS_TESTFLIGHT_URL --body 'https://testflight.apple.com/join/...'
   gh workflow run pages.yml
   ```

외부 테스터는 최대 10,000명이고 빌드는 90일 후 만료되므로 그 전에 새 빌드를 올려야 합니다. 심사에는 개인 PAT를 제공하지 않고 데모 모드를 안내합니다.

심사 메모:

```text
This app reads temperature and humidity from a user's LG ThinQ humidifier using a Personal Access Token the user issues from LG. It is not affiliated with LG.

No sign-in is needed for review. On the first screen, tap "샘플 데이터로 체험하기" (Try with sample data). The app then shows on-device sample values, clearly labelled as sample data, and the home screen widget shows the same values. Open the gear icon and tap "체험 종료" to end the demo.
```

### TestFlight 자동 갱신

TestFlight 빌드는 올린 날부터 90일이 지나면 실행되지 않습니다. `Keep TestFlight builds current` 워크플로가 매주 월요일 오전 9시(한국 시간)에 외부 테스터가 받은 최신 빌드의 만료일을 확인하고, 20일 이하로 남으면 새 빌드를 올립니다. 업로드 워크플로는 Apple 처리가 끝나면 "테스트할 내용"을 적고 외부 그룹에 추가한 뒤 베타 심사에 제출합니다. 같은 버전의 이후 빌드는 전체 심사를 다시 거치지 않을 수 있지만, 보장되지는 않습니다.

같은 점검에서 배포 인증서나 App Store 프로파일이 30일 안에 만료되면 실행이 실패로 끝나 GitHub이 메일을 보냅니다. 그때 `asc.py setup`(인증서 Secret을 지운 뒤)과 `asc.py appstore-profiles`로 갱신합니다. 이미 설치된 TestFlight 빌드는 Apple이 다시 서명하므로 인증서 만료와 관계없이 90일까지 실행됩니다.

### Firebase로 등록한 iPhone에 배포

**친구에게는 TestFlight 공개 링크가 기본입니다.** Firebase는 TestFlight 심사가 거절됐을 때의 대비책으로만 씁니다. 릴리즈 태그마다 자동 배포는 유지합니다.

TestFlight와 별개로, 초대한 사람의 iPhone에만 설치되는 빌드를 Firebase App Distribution으로 보냅니다. 심사가 없고, 친구는 초대 메일에서 기기를 등록하므로 UDID를 직접 찾을 필요가 없습니다. 빌드에는 등록 기기의 UDID가 들어 있어 Firebase로만 전달하고 GitHub에는 올리지 않습니다. 앱에는 Firebase SDK가 없습니다.

한 번만 설정합니다.

1. [Firebase 콘솔](https://console.firebase.google.com)에서 프로젝트를 만듭니다. Google 애널리틱스는 필요 없습니다.
2. 프로젝트 설정 → 내 앱 → iOS 앱 추가에서 번들 ID `dev.thinqclimate.widget`을 입력합니다. 구성 파일 다운로드와 SDK 추가 단계는 건너뜁니다. 화면의 **앱 ID**(`1:…:ios:…`)를 적어 둡니다.
3. App Distribution을 열고 테스터 및 그룹에서 그룹을 만듭니다. 별칭은 `friends`로 하고 친구 이메일을 추가합니다.
4. 서비스 계정 키를 **앱과 같은 프로젝트에서** 만듭니다. Firebase 콘솔의 프로젝트 설정 → **서비스 계정** 탭 → *Google Cloud에서 서비스 계정 관리*를 누르면 올바른 프로젝트가 열립니다. 서비스 계정을 만들어 **Firebase App Distribution Admin** 역할을 주고, 키 → 키 추가 → JSON으로 내려받습니다. Google Cloud 콘솔을 따로 열면 위쪽 프로젝트 선택이 다른 프로젝트일 수 있으니 주의하세요.
5. 키와 앱 ID를 저장합니다. 명령이 키로 앱의 프로젝트와 테스터 그룹에 실제로 접근해 본 뒤에만 저장하며, 다른 프로젝트의 키면 프로젝트 번호를 알려 주고 멈춥니다. 키 파일은 GitHub Secrets에만 들어가며, 저장 후 지워도 됩니다.

   ```sh
   python3 scripts/asc.py firebase --key ~/Downloads/<키>.json --app-id 1:…:ios:…
   ```

이미 Apple 개발자 계정에 등록된 iPhone은 Firebase에서 다시 등록할 필요 없이 빌드에 넣을 수 있습니다. Firebase에는 UDID를 넣는 기능이 없지만, 설치를 허용하는 기기 목록은 Apple 프로파일이 정하기 때문입니다. 테스터는 Firebase 초대만 받으면 됩니다. 계정에는 업무용 기기도 있을 수 있으니 `--dry-run`으로 먼저 보고 `--only`로 고르세요.

```sh
python3 scripts/asc.py import-devices --apple --only "iPhone 13 mini" --key ~/Downloads/AuthKey_XXXX.p8 --key-id XXXX --issuer YYYY --dry-run
```

친구가 기기를 등록할 때마다 반복합니다.

1. 친구가 iPhone의 Safari에서 초대 메일을 열어 수락하고, 안내대로 기기를 등록합니다. Firebase가 새 기기 알림 메일을 보냅니다.
2. App Distribution → 테스터 및 그룹에서 **Apple UDID 내보내기**로 파일을 받습니다.
3. 파일을 넣으면 기기 목록을 저장하고 새 빌드를 시작합니다. 빌드가 끝나면 Firebase가 테스터에게 설치 메일을 보냅니다.

   ```sh
   python3 scripts/asc.py import-devices ~/Downloads/<내보낸 파일>.txt
   ```

`v*` 태그로 릴리즈하면 Firebase에도 자동 배포되고, TestFlight 심사가 거절되면 감시 워크플로가 한 번 빌드를 보냅니다. 친구 iPhone에서 설정 → 개인정보 보호 및 보안 → 개발자 모드를 켜야 할 수 있습니다. Apple 계정은 1년에 iPhone 100대까지 등록할 수 있습니다.

### App Store 공개 배포

TestFlight와 같은 빌드를 App Store에도 올립니다. 앱은 무료이고 광고가 없습니다.

- 스토어 문구는 `appstore/metadata/<언어>/`(설명, 부제, 키워드, 홍보 문구, 업데이트 내용), 심사 메모는 `appstore/review_notes.txt`에 있습니다. 키워드에는 다른 회사 상표(LG, ThinQ)를 넣지 않습니다.
- 스크린샷은 `bash scripts/store-screenshots.sh`가 샘플 데이터 모드로 iPhone 6.9"와 iPad 13" 시뮬레이터에서 찍어 `appstore/screenshots/`에 저장합니다. 화면이 바뀌면 다시 실행해 커밋합니다.
- `Submit iOS to the App Store` 워크플로(`scripts/appstore.py publish`)가 카테고리(유틸리티), 연령 등급, 무료 가격, 전체 국가, 스토어 문구, 스크린샷, 심사 연락처를 채우고 심사에 제출합니다. 승인되면 자동으로 출시됩니다.
- `APPSTORE_ENABLED=true`이면 릴리즈 태그마다 TestFlight 업로드 뒤 같은 빌드를 App Store 심사에 제출합니다. 이미 출시된 버전 번호로는 제출할 수 없으므로 `MARKETING_VERSION`을 올려야 합니다.
- 심사 결과는 `Watch TestFlight review`가 매시간 확인해 "App Store 1.5.0 출시됨" 또는 "심사 거절됨" 이슈를 만들어 메일로 알립니다.

API로 할 수 없어 App Store Connect 웹에서 한 번만 하는 일:
1. 앱 → 앱 개인정보 보호(App Privacy) → 시작하기 → "데이터를 수집하지 않음" → 게시.
2. 비즈니스(Business) → 규정 준수(Compliance) → Digital Services Act에서 EU 판매자(Trader) 여부 신고. Trader가 아니면 EU에는 배포되지 않습니다.

### GitHub에 저장하는 값 한눈에 보기

직접 입력하지 말고 표의 명령으로 저장하세요. 명령이 형식과 실제 접근 가능 여부를 확인한 뒤 저장합니다. 예시는 형식만 보여 주며 실제 값이 아닙니다.

| 이름 | 종류 | 형식 예시 | 값을 확인하는 곳 | 저장 방법 |
|---|---|---|---|---|
| `IOS_TEAM_ID` | Variable | `L78W862TSU` (10자) | developer.apple.com → 계정 → 멤버십 세부 사항 → 팀 ID | `gh variable set` |
| `IOS_BUNDLE_ID` | Variable | `dev.thinqclimate.widget` | `ios/project.yml`의 `CLIMATE_BUNDLE_ID` | `gh variable set` |
| `IOS_APP_GROUP` | Variable | `group.dev.thinqclimate.widget` | `ios/project.yml`의 `CLIMATE_APP_GROUP` | `gh variable set` |
| `IOS_TESTFLIGHT_URL` | Variable | `https://testflight.apple.com/join/AbCd1234` | App Store Connect → TestFlight → 외부 그룹 → 공개 링크 | `gh variable set` |
| `ASC_API_KEY_P8_BASE64` | Secret | `LS0tLS1CRUdJTi…` (`.p8` 파일의 base64) | App Store Connect → 사용자 및 액세스 → 통합 → 팀 키 (파일은 한 번만 다운로드) | `asc.py setup` |
| `ASC_API_KEY_ID` | Secret | `6WFK8R588T` (10자) | 같은 화면의 키 ID 열 | `asc.py setup` |
| `ASC_API_ISSUER_ID` | Secret | `a0ac3858-0a34-429f-…` (UUID) | 같은 화면 위쪽의 Issuer ID | `asc.py setup` |
| `IOS_DISTRIBUTION_CERT_ID`, `IOS_DISTRIBUTION_P12_BASE64`, `IOS_DISTRIBUTION_P12_PASSWORD` | Secret | 스크립트가 만든 인증서 | 직접 만들지 않습니다 | `asc.py setup`이 생성 |
| `IOS_ADHOC_DEVICES` | Secret | `민수의 iPhone\|00008110-001A2B3C4D5E6F78` (한 줄에 한 대) | Firebase → App Distribution → 테스터 및 그룹 → Apple UDID 내보내기, 또는 Apple 계정의 등록 기기 | `asc.py import-devices` |
| `FIREBASE_SERVICE_ACCOUNT_BASE64` | Secret | `ewogICJ0eXBlIjog…` (JSON 키의 base64) | Firebase → 프로젝트 설정 → 서비스 계정. JSON의 `project_id`가 프로젝트 설정의 **프로젝트 ID**와 같아야 합니다 | `asc.py firebase` |
| `FIREBASE_IOS_APP_ID` | Variable | `1:439215807830:ios:9e873ad3…` | Firebase → 프로젝트 설정 → 일반 → 내 앱 → 앱 ID. 가운데 숫자가 **프로젝트 번호**입니다 | `asc.py firebase` |
| `FIREBASE_GROUPS` | Variable | `friends` | Firebase → App Distribution → 테스터 및 그룹 → 그룹 별칭 | `asc.py firebase --groups` |
| `APPSTORE_REVIEW_CONTACT` | Secret | `{"firstName":"길동","lastName":"홍","phone":"+821000000000","email":"me@example.com"}` | App Store 심사관만 보는 연락처(공개되지 않음) | `gh secret set APPSTORE_REVIEW_CONTACT` |
| `APPSTORE_ENABLED` | Variable | `true` | 릴리즈 태그마다 App Store 심사에 자동 제출할지 | `gh variable set` |
| `ANDROID_KEYSTORE_BASE64` 외 3개 | Secret | keystore의 base64, 암호, alias | 로컬 `android/keystore/release.jks`, `.env.signing` | 위 Android 서명 절 참고 |

키 파일과 앱 ID가 같은 프로젝트인지 직접 확인하려면 Firebase 프로젝트 설정 → 일반에서 **프로젝트 ID**(JSON의 `project_id`와 비교)와 **프로젝트 번호**(앱 ID 가운데 숫자와 비교)를 보면 됩니다.

### GitHub Actions로 업로드(선택)

로컬 업로드 대신 CI를 쓸 때만 필요합니다. 다음 값을 GitHub 저장소 Settings → Secrets and variables → Actions에 설정합니다. 비밀 값은 채팅/이슈에 올리지 마세요.

Variables:

| Variable | 값 |
|---|---|
| `IOS_TEAM_ID` | Apple Developer Team ID |
| `IOS_BUNDLE_ID` | 등록한 앱 Bundle ID, 기본 `dev.thinqclimate.widget` |
| `IOS_APP_GROUP` | 두 타깃에 등록한 App Group, 기본 `group.dev.thinqclimate.widget` |
| `IOS_TESTFLIGHT_URL` | 실제 생성한 `https://testflight.apple.com/join/...` 공개 초대 링크 |
| `IOS_TESTFLIGHT_ENABLED` | 태그 릴리즈 후 자동 업로드할 때만 `true` |
| `IOS_BUILD_NUMBER` | 최소 빌드 번호(선택). App Store Connect의 가장 높은 빌드 번호 + 1과 비교해 더 큰 값을 쓰므로, 등록해 둬도 같은 번호를 재사용하지 않습니다 |

Secrets:

| Secret | 값 |
|---|---|
| `IOS_DISTRIBUTION_P12_BASE64` | 개인 키를 포함한 Apple Distribution 인증서 `.p12`의 base64 |
| `IOS_DISTRIBUTION_P12_PASSWORD` | 해당 `.p12` 암호(빈 암호이면 생략 가능) |
| `IOS_APP_PROFILE_BASE64` | 앱용 App Store 배포 프로파일의 base64 |
| `IOS_WIDGET_PROFILE_BASE64` | `.extension` 타깃용 App Store 배포 프로파일의 base64 |
| `ASC_API_KEY_P8_BASE64` | App Store Connect API 키 `.p8`의 base64 |
| `ASC_API_KEY_ID` | API Key ID |
| `ASC_API_ISSUER_ID` | API Issuer ID |

두 프로파일 모두 App Group/공유 Keychain 권한이 맞아야 합니다. 스크립트는 Team/Bundle ID/App Group과 배포 프로파일 종류를 확인하고 인증 자료를 임시 keychain에 가져온 뒤 Release archive를 App Store Connect에 업로드합니다. 임시 인증 자료는 종료 시 삭제합니다.

처음에는 Actions → **Upload iOS to TestFlight** → Run workflow에서 **새롭고 증가하는 build number**를 넣어 실행하세요. 수동 업로드와 태그 자동 업로드를 섞는 경우 이미 사용한 빌드 번호를 재사용하지 않도록 `IOS_BUILD_NUMBER`를 관리하세요. 앱의 마케팅 버전은 `ios/project.yml`의 `MARKETING_VERSION`입니다.

Apple에서 빌드 처리가 끝난 뒤 외부 테스트 그룹/심사/공개 링크를 설정하고 `IOS_TESTFLIGHT_URL`을 등록합니다. 이후 릴리즈 설명은 실제 링크를 포함합니다. 기존 릴리즈 설명은 링크 등록 후 직접 갱신해야 합니다. 단순한 시뮬레이터 빌드 성공을 iPhone 설치 검증이나 TestFlight 배포 성공으로 표시하지 않습니다.

공식 근거: [GitHub Releases 생성](https://cli.github.com/manual/gh_release_create), [Apple TestFlight](https://developer.apple.com/testflight/), [외부 테스터 초대](https://developer.apple.com/help/app-store-connect/test-a-beta-version/invite-external-testers), [Apple 배포 방법](https://help.apple.com/xcode/mac/current/en.lproj/dev31de635e5.html).
