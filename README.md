# ThinQ Climate

LG ThinQ에 등록된 **가습기의 현재 온도·습도**를 iPhone과 Android 홈 화면에서 확인하는 네이티브 앱입니다. 별도 서버나 구독 없이 공식 ThinQ Connect API를 읽기 전용으로 사용합니다. 한국어 설정 화면, 기기 선택, 마지막 수신 시각, 오래된 데이터 표시, 오류 안내를 제공합니다.

- **iOS 17+**: SwiftUI 앱 + WidgetKit 작은/중간 위젯. App Group 캐시와 공유 Keychain.
- **Android 8+ (API 26)**: 네이티브 앱 + 크기 조절 가능한 RemoteViews 위젯. WorkManager 갱신과 Android Keystore 암호화.
- 여러 가습기를 검색하고 하나를 선택합니다. 현재 버전의 모든 위젯은 같은 선택 기기를 표시합니다.
- LG 비밀번호를 받지 않습니다. PAT는 폰에만 저장하며 로그·위젯·저장소에 출력하지 않습니다.

## 시작하기

1. LG ThinQ 앱에 가습기가 등록되어 있는지 확인합니다.
2. [LG PAT 발급 페이지](https://connect-pat.lgthinq.com)에서 **같은 LG 계정**으로 로그인합니다. 기기 목록과 상태 조회에 필요한 읽기 권한을 허용합니다. PAT를 이슈나 채팅에 올리지 마세요.
3. 앱에 PAT, LG 계정 국가 코드(한국 `KR`), 서버 지역(한국 `kic`)을 입력합니다. 미주 계정은 `aic`, 유럽/기타는 `eic`를 선택합니다. 앱 언어나 현재 위치가 아니라 **LG 계정 지역**을 사용합니다.
4. **연결하고 가습기 찾기** → 가습기 선택 → 새로고침으로 수신값을 확인합니다.
5. 홈 화면을 길게 누르고 위젯 목록에서 **ThinQ Climate**를 추가합니다.

가습기가 ThinQ에 보이더라도 공식 API 지원 여부와 센서 공개 범위는 모델·펌웨어·계정 지역에 따라 다릅니다. 제공되지 않는 값은 `—`입니다. **목표 습도를 현재 습도로 대신 표시하지 않습니다.** 실제 사용자 가습기와의 연동 검증에는 사용자 기기에서 PAT를 설정해야 합니다.

## 갱신 방식

30분마다 갱신을 요청합니다. iOS WidgetKit과 Android 절전 정책이 실제 실행 시점을 결정하므로 실시간 표시나 정확한 30분 갱신을 보장하지 않습니다. 수동 요청은 1분 이내 중복 요청을 제한합니다. 네트워크 실패 시 마지막 성공값과 오류를 함께 표시하고, 90분 이상 지난 값에는 오래된 데이터 표시가 붙습니다. 위젯 시각은 **앱이 서버 응답을 받은 시각**이며 센서 자체의 측정 시각은 아닙니다.

기기를 바꾸면 이전 센서 캐시를 지웁니다. Android는 세대 식별자로 이전 요청의 늦은 결과를 버리고, iOS는 앱과 확장 사이 파일 잠금으로 변경과 갱신을 직렬화합니다. 연결 해제는 인증과 캐시를 삭제합니다. LG 쪽 PAT 자체를 폐기하려면 PAT 발급 페이지에서도 취소하세요.

## 빌드와 설치

### Android

JDK 17, Android SDK 35, **Gradle 8.9**가 필요합니다. Android Studio에서 `android/`를 열거나 다음 명령을 실행하세요. Gradle 실행 파일은 별도 설치합니다(예: `sdk install gradle 8.9`).

```sh
cd android
gradle :core:check :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
# USB 디버깅을 켠 Android 또는 에뮬레이터
adb install -r app/build/outputs/apk/debug/app-debug.apk
gradle :app:connectedDebugAndroidTest
```

CI가 성공하면 Actions의 `android-debug-apk` 아티팩트에서 설치용 디버그 APK를 받을 수 있습니다. 배포용 서명 키는 포함하지 않았습니다.

### iPhone

macOS, Xcode 16+, XcodeGen이 필요합니다.

```sh
brew install xcodegen
swift test --package-path ios
cd ios
xcodegen generate
open ThinQClimate.xcodeproj
```

실기기에서는 두 타깃의 Signing Team을 본인 Apple 개발자 계정으로 지정하고 고유한 Bundle ID를 사용하세요. 프로젝트 설정 `CLIMATE_APP_GROUP`을 본인 계정에 등록한 App Group으로 바꿉니다. **앱과 확장 양쪽에 같은 App Group 및 Keychain Sharing 권한**이 필요합니다. `CLIMATE_KEYCHAIN_GROUP`은 `$(AppIdentifierPrefix)` 접두어를 유지하세요. 생성된 entitlements와 Info.plist는 `ios/project.yml`에서 관리합니다. Signing 설정 후 iPhone을 선택하여 실행합니다.

CI의 iOS 아티팩트는 **시뮬레이터용 앱**입니다. 실기기 설치용 IPA는 Apple 서명과 프로비저닝이 필요합니다. 이 저장소는 앱스토어 등록·TestFlight 배포를 자동 수행하지 않습니다.

## 검증

```sh
bash scripts/test-core.sh
```

외부 의존성 없이 Android 코어의 센서 경계값, 자료형, 기기 필터, 요청 헤더·URL·메시지 ID, 오류 분류, 데이터 유효기간을 검사합니다. 앱 단위 테스트는 실제 HTTP 어댑터에 모의 연결을 주입해 JSON, 리소스 정리, 타임아웃, 인증·한도·오프라인 오류와 리다이렉트 차단을 검사합니다. Android 계측 테스트는 실제 Keystore 저장/복호화, 인증 삭제, 기기 변경 캐시 초기화를 확인합니다. Swift 테스트는 동일한 API 계약과 센서·오류·캐시 직렬화를 검사합니다.

[Mobile CI](../../actions/workflows/ci.yml)는 Android 빌드·lint·단위 테스트·에뮬레이터 계측 테스트, Swift 테스트, iOS 앱과 WidgetKit 확장 빌드를 수행합니다. **통과 여부는 실제 Actions 실행 결과를 확인하세요.** 구현 당시 로컬 검증 범위와 미검증 항목은 [검증 기록](docs/VALIDATION.md)에 구분해 적었습니다. 실기기 위젯·절전·PAT 연동의 최종 확인은 [수동 점검표](docs/DEVICE_TESTING.md)를 따릅니다.

## 구조와 자료

- `android/core`: 외부 의존성 없는 API 계약과 센서 모델
- `android/app`: HTTP, 암호화 저장, 설정 화면, WorkManager, 위젯
- `ios/Shared/Core`: Swift Package로도 테스트하는 API 계약과 센서 모델
- `ios/Shared/Runtime`: 공유 Keychain·캐시와 프로세스 간 갱신 잠금
- `ios/App`, `ios/Widget`: 설정 앱과 WidgetKit 위젯
- [API 근거와 설계 결정](docs/ARCHITECTURE.md), [보안 안내](SECURITY.md)

LG 공식 SDK의 가습기 매핑을 근거로 `airQualitySensor.temperature`(°C), `airQualitySensor.humidity`(%)를 읽습니다. 비공식 ThinQ 로그인 역공학 API는 사용하지 않습니다. LG Electronics의 공식 앱이 아니며 LG ThinQ는 해당 소유자의 상표입니다.
