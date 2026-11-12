# Climate Widget for ThinQ

LG ThinQ 기기의 현재 온도·습도를 휴대폰과 홈 화면 위젯에서 확인합니다. 별도 앱 서버나 구독 없이 공식 ThinQ Connect API를 읽기 전용으로 사용합니다. LG Electronics의 공식 앱이 아닙니다.

## 설치하기

**[Android APK 다운로드](https://github.com/seyongida/lgthinq-climate-widget/releases/latest/download/ThinQ-Climate.apk)** · [릴리즈와 체크섬](https://github.com/seyongida/lgthinq-climate-widget/releases/latest)

- Android 8 이상: APK를 다운로드하고, Android 안내에 따라 해당 브라우저의 앱 설치를 허용합니다.
- iPhone/iPad: 앱 이름은 **Climate Widget for ThinQ**, 홈 화면 표시는 Climate Widget입니다. **TestFlight 배포 준비 중**입니다. 베타 심사 후 릴리즈 페이지와 [설치 페이지](https://seyongida.github.io/lgthinq-climate-widget/)에 실제 초대 링크를 안내합니다. 시뮬레이터 ZIP이나 서명되지 않은 IPA는 iPhone 설치 파일이 아닙니다.

처음 실행하면 **본인의 LG ThinQ Personal Access Token(PAT)**을 입력합니다. 권한은 **기기 목록·상태 조회**가 필요하고, **에너지 사용량 조회**를 더하면 전기 사용량도 보여 줍니다. 제어 권한은 필요 없습니다. 공개 APK에는 개발자의 토큰이 포함되지 않습니다. iOS 앱은 LG 계정 없이 **샘플 데이터로 체험하기**도 지원합니다. 샘플 값은 기기에서 만들며 실제 측정값이 아닙니다.

1. [LG PAT 발급 페이지](https://connect-pat.lgthinq.com)에서 ThinQ와 같은 계정으로 기기 목록·상태 읽기 권한을 발급합니다.
2. 앱에 PAT, LG 계정 국가 코드(한국 `KR`), 계정 서버 지역(한국 `kic`, 미주 `aic`, 유럽/기타 `eic`)을 입력합니다.
3. 연결 확인이 끝나면 기기를 선택하고 현재 센서값을 확인합니다.
4. **연결 설정**에서 언제든 새 PAT로 교체합니다. 기존 토큰은 화면에 표시하지 않습니다. 새 토큰의 연결 확인이 실패하면 기존 연결을 유지합니다. 성공하면 이전 계정의 센서 캐시를 지웁니다.

PAT를 채팅이나 이슈에 올리지 마세요. 서버 측 토큰 폐기는 LG 발급 페이지에서 수행합니다. 센서 공개 범위는 모델·펌웨어·계정 지역에 따라 다르며 미지원 값은 `—`입니다. 목표 습도를 현재 습도로 대신 표시하지 않습니다.

## 무엇을 보여 주나요

ThinQ 앱을 열고 기기마다 들어가지 않아도 집 상태를 한 화면에서 봅니다. 앱은 상태를 **읽기만** 하고 기기를 조작하지 않습니다.

- **지금 우리 집:** 공기 기기의 평균 습도·온도, 쾌적 여부(습도 40~60%), 공기질과 가장 높은 PM2.5
- **곧 끝나요:** 세탁기·건조기·스타일러·식기세척기·오븐의 남은 시간을 1초 단위로 표시합니다. 남은 시간은 마지막으로 받은 값에서 계산한 예상입니다.
- **확인해 주세요:** 끝났는데 꺼내지 않은 세탁·건조, 냉장고 문 열림, 오류
- **전기 사용량:** 이번 달 어제까지의 기기별 사용량과 지난달 같은 기간 비교. 토큰에 에너지 조회 권한이 있고 모델이 지원할 때만 보입니다. 요금은 계약 조건을 몰라 표시하지 않습니다.
- **모든 기기:** ThinQ Connect가 제공하는 모든 기기의 한 줄 요약. 꺼져 있거나 오프라인인 기기는 흐리게 표시합니다.

ThinQ Connect API가 지원하는 기기만 나옵니다. ThinQ 앱에 등록된 일부 기기(구형 모델 등)는 API에 나오지 않습니다.

## 위젯과 잠금 화면

- **공기 위젯:** Android는 1×1·1×2·2×1, iOS는 작은·중간 크기와 잠금 화면. 기기, 색상 6종, 직접 색상(`#RRGGBB`), 불투명도(40~100%)를 위젯마다 정합니다. Android는 위젯을 누르고, iOS는 위젯을 길게 눌러 **위젯 편집**에서 바꿉니다. 아이폰 홈 화면에는 1×1 위젯이 없습니다.
- **가전 타이머 위젯:** 진행 중인 가전과 남은 시간, 확인할 일을 보여 줍니다. 네트워크 없이도 시간이 줄어듭니다.
- **잠금 화면 남은 시간:** iOS는 실시간 현황(Live Activity)과 Dynamic Island, Android는 진행 알림으로 보여 줍니다. 실제로 끝난 가전만 "끝났어요"로 바뀌며, 설정에서 끌 수 있습니다. iOS는 앱을 열 때 시작·갱신됩니다.

기존의 큰 위젯 크기는 런처에 남을 수 있습니다. 1×1을 새로 추가하거나 크기를 줄이세요. 실제 셀 크기는 홈 화면 격자와 런처 설정에 따릅니다.

30분마다 갱신을 요청하며 절전/네트워크 상태에 따라 실행이 지연될 수 있습니다. 수동 요청은 기기별 최소 1분 간격입니다. 실패 시 마지막 성공값과 오류를 함께 표시하고, 90분 이상 지난 값은 오래됨으로 구분합니다. 시각은 앱이 서버 응답을 받은 시각입니다.

## 공개 Release / 개인 Dev 빌드

| 항목 | 공개 Release | 개인 Debug/Dev |
|---|---|---|
| Android 앱 ID | `dev.thinqclimate.widget.release` | `dev.thinqclimate.widget` |
| 앱 이름 | Climate Widget | Climate Widget Dev |
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
python3 scripts/verify-release.py android/app/build/outputs/apk/release/app-release.apk --version v1.5.0
```

Apple 개발 환경:

```sh
swift test --package-path ios
xcodegen generate --spec ios/project.yml
open ios/ThinQClimate.xcodeproj
```

실기기 빌드는 Apple Team, 고유 Bundle ID, App Group과 두 타깃의 Keychain 공유 권한이 필요합니다. [Mobile CI](https://github.com/seyongida/lgthinq-climate-widget/actions/workflows/ci.yml)는 Android 코어/HTTP 계약·두 빌드 유형·lint·에뮬레이터 계측 검사와 Swift 테스트·iOS 시뮬레이터 빌드를 수행합니다. [릴리즈 워크플로](https://github.com/seyongida/lgthinq-climate-widget/actions/workflows/release.yml)는 `v*` 태그에서 공개 APK를 만듭니다.

자료: [배포 운영](docs/DISTRIBUTION.md) · [설계와 API 근거](docs/ARCHITECTURE.md) · [보안](SECURITY.md) · [검증 기록](docs/VALIDATION.md) · [실기기 점검표](docs/DEVICE_TESTING.md)
