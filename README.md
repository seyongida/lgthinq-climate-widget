# Climate Widget for ThinQ

LG ThinQ 기기의 현재 온도·습도를 휴대폰과 홈 화면 위젯에서 확인합니다. 별도 앱 서버나 구독 없이 공식 ThinQ Connect API를 읽기 전용으로 사용합니다. LG Electronics의 공식 앱이 아닙니다.

## 설치하기

**[Android APK 다운로드](https://github.com/seyongida/lgthinq-climate-widget/releases/latest/download/ThinQ-Climate.apk)** · [릴리즈와 체크섬](https://github.com/seyongida/lgthinq-climate-widget/releases/latest)

- Android 8 이상: 앱 이름은 ThinQ Climate입니다. APK를 다운로드하고, Android 안내에 따라 해당 브라우저의 앱 설치를 허용합니다.
- iPhone/iPad: 앱 이름은 **Climate Widget for ThinQ**, 홈 화면 표시는 Climate Widget입니다. **TestFlight 배포 준비 중**입니다. 베타 심사 후 릴리즈 페이지와 [설치 페이지](https://seyongida.github.io/lgthinq-climate-widget/)에 실제 초대 링크를 안내합니다. 시뮬레이터 ZIP이나 서명되지 않은 IPA는 iPhone 설치 파일이 아닙니다.

처음 실행하면 **본인의 LG ThinQ Personal Access Token(PAT)**을 입력합니다. 공개 APK에는 개발자의 토큰이 포함되지 않습니다. iOS 앱은 LG 계정 없이 **샘플 데이터로 체험하기**도 지원합니다. 샘플 값은 기기에서 만들며 실제 측정값이 아닙니다.

1. [LG PAT 발급 페이지](https://connect-pat.lgthinq.com)에서 ThinQ와 같은 계정으로 기기 목록·상태 읽기 권한을 발급합니다.
2. 앱에 PAT, LG 계정 국가 코드(한국 `KR`), 계정 서버 지역(한국 `kic`, 미주 `aic`, 유럽/기타 `eic`)을 입력합니다.
3. 연결 확인이 끝나면 기기를 선택하고 현재 센서값을 확인합니다.
4. **연결 설정**에서 언제든 새 PAT로 교체합니다. 기존 토큰은 화면에 표시하지 않습니다. 새 토큰의 연결 확인이 실패하면 기존 연결을 유지합니다. 성공하면 이전 계정의 센서 캐시를 지웁니다.

PAT를 채팅이나 이슈에 올리지 마세요. 서버 측 토큰 폐기는 LG 발급 페이지에서 수행합니다. 센서 공개 범위는 모델·펌웨어·계정 지역에 따라 다르며 미지원 값은 `—`입니다. 목표 습도를 현재 습도로 대신 표시하지 않습니다.

## 기기와 위젯

- Android: 가습기·공기청정기·공기청정 팬을 여러 대 선택합니다. 위젯마다 다른 기기를 지정할 수 있습니다.
- **1×1**은 첫 번째 기기, **1×2 / 2×1**은 최대 두 기기를 표시합니다. 앱의 **홈 화면 위젯** 선택 카드나 런처 위젯 목록에서 추가하세요.
- 위젯 본문을 누르면 기기와 색상을 바꿉니다. 6가지 프리셋, 배경/습도 강조색의 `#RRGGBB` 입력, 배경 불투명도(40~100%)와 실제 미리보기를 지원합니다. **이 위젯에 적용**을 눌러야 저장합니다.
- 아래 수신 시각/↻를 누르면 새로고침합니다. 작은 크기는 시각을 생략할 수 있습니다. 크기를 줄였다 늘려도 두 번째 기기와 색상 설정은 유지합니다.
- iOS 17 이상: 가습기 한 대와 WidgetKit 작은/중간 위젯을 지원합니다. Android의 다중 기기/색상 설정은 iOS에 아직 적용하지 않았습니다.

기존의 큰 위젯 크기는 런처에 남을 수 있습니다. 1×1을 새로 추가하거나 크기를 줄이세요. 실제 셀 크기는 홈 화면 격자와 런처 설정에 따릅니다.

30분마다 갱신을 요청하며 절전/네트워크 상태에 따라 실행이 지연될 수 있습니다. 수동 요청은 기기별 최소 1분 간격입니다. 실패 시 마지막 성공값과 오류를 함께 표시하고, 90분 이상 지난 값은 오래됨으로 구분합니다. 시각은 앱이 서버 응답을 받은 시각입니다.

## 공개 Release / 개인 Dev 빌드

| 항목 | 공개 Release | 개인 Debug/Dev |
|---|---|---|
| Android 앱 ID | `dev.thinqclimate.widget.release` | `dev.thinqclimate.widget` |
| 앱 이름 | ThinQ Climate | ThinQ Climate Dev |
| `.env` PAT | **항상 제외** | 선택적으로 내장 |
| 최초 연결 | 사용자 PAT 입력 | 내장 PAT가 있으면 자동 연결, 없으면 입력 |
| 토큰 교체 | 연결 설정에서 가능 | 연결 설정에서 가능 |
| 배포 | 서명된 APK를 GitHub Releases에 공개 | 로컬 개인 설치 또는 토큰 없는 CI 검사 |

두 Android 앱은 함께 설치할 수 있으며 데이터와 위젯 설정은 분리됩니다. 이전 개인 디버그 앱의 서명/설정을 보존하기 위해 Dev 앱 ID를 유지했습니다. iOS는 두 빌드 모두 사용자 PAT를 직접 입력하며 `.env`를 읽지 않습니다.

## 개발과 검증

JDK 17, Android SDK 35, 포함된 Gradle 8.9 Wrapper를 사용합니다.

```sh
cd android
./gradlew :core:check :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
```

개인 자동 연결이 필요할 때만 루트 `.env.example`을 `.env`로 복사하고 `THINQ_PAT`, `THINQ_COUNTRY`, `THINQ_REGION`을 넣습니다. 환경 변수가 파일보다 우선합니다. **실제 토큰을 내장한 Dev APK/테스트 APK는 공유하지 마세요.**

서명 키가 없으면 Release 빌드는 설치할 수 없는 unsigned APK를 만듭니다. 서명과 공개 방법은 [배포 운영 안내](docs/DISTRIBUTION.md)를 참고하세요. 공개 전에는 다음 검사를 수행합니다.

```sh
python3 scripts/verify-release.py android/app/build/outputs/apk/release/app-release.apk --version v1.3.0
```

Apple 개발 환경:

```sh
swift test --package-path ios
xcodegen generate --spec ios/project.yml
open ios/ThinQClimate.xcodeproj
```

실기기 빌드는 Apple Team, 고유 Bundle ID, App Group과 두 타깃의 Keychain 공유 권한이 필요합니다. [Mobile CI](https://github.com/seyongida/lgthinq-climate-widget/actions/workflows/ci.yml)는 Android 코어/HTTP 계약·두 빌드 유형·lint·에뮬레이터 계측 검사와 Swift 테스트·iOS 시뮬레이터 빌드를 수행합니다. [릴리즈 워크플로](https://github.com/seyongida/lgthinq-climate-widget/actions/workflows/release.yml)는 `v*` 태그에서 공개 APK를 만듭니다.

자료: [배포 운영](docs/DISTRIBUTION.md) · [설계와 API 근거](docs/ARCHITECTURE.md) · [보안](SECURITY.md) · [검증 기록](docs/VALIDATION.md) · [실기기 점검표](docs/DEVICE_TESTING.md)
