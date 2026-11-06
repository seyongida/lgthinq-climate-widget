# 검증 기록

2026-11-06. 실제 실행과 준비된 자동화를 구분합니다.

| 검증 | 결과 | 근거 |
|---|---|---|
| Android 순수 Java 코어 | 통과 | JVM에서 3,142 assertions 실행 |
| 실제 ThinQ HTTP 어댑터 계약 | 통과 | org.json 20240303 소스로 컴파일, 모의 HTTP 연결 55 assertions 실행 |
| Android 리소스 XML | 통과 | Python XML 파서 전체 파일 확인 |
| Apple 개인정보 manifest | 통과 | plistlib 파서로 앱/확장 파일 확인 |
| 프로젝트/CI YAML | 통과 | YAML 파서 확인 |
| Android APK·lint·Gradle 단위 테스트 | 통과 | Mobile CI #4 android job |
| Android Keystore·캐시 계측 테스트 | 통과 | Mobile CI #4 android-device job (API 35 emulator), 4 tests / 0 failures, 늦은 응답 거부 포함 |
| Swift 테스트 및 iOS 앱/확장 빌드 | 통과 | Mobile CI #4 ios job (macOS), Swift 8 tests / 0 failures |
| LG 실제 PAT 및 가습기 센서 | 미실행 | 사용자 인증·모델이 제공되지 않음 |
| iPhone/Android 실제 홈 화면 위젯 | 미실행 | 실기기 및 iOS 서명 계정 필요 |

로컬 환경에는 JDK compiler 모듈은 있으나 javac 실행 파일/ct.sym이 없어 `java com.sun.tools.javac.Main -source 17 -target 17`을 사용했습니다. 표준 JDK 17 환경은 CI에서 `--release 17` 및 Gradle로 검증합니다. HTTP 계약 로컬 검증의 JSON 라이브러리는 GitHub 연결 도구를 통해 공식 JSON-java 20240303 소스를 임시 디렉터리에 받아 컴파일했으며 저장소에 외부 소스를 복사하지 않았습니다. 일반 재현 명령은 `cd android && ./gradlew :core:check :app:testDebugUnitTest`입니다.

로컬에는 Android SDK·Gradle·Swift·Xcode가 없고 환경의 다운로드 프록시 연결이 거부되어 플랫폼 도구를 추가 설치할 수 없었습니다. 플랫폼 테스트 결과를 로컬 통과로 표현하지 않습니다. 실제 Actions 결과는 이 문서의 작성 시점과 다를 수 있으므로 저장소 Actions를 확인하세요.

플랫폼 검증 근거: [Mobile CI #4](https://github.com/seyongida/lgthinq-climate-widget/actions/runs/42150170556). 검증한 커밋 `dca217d7fa4e97d6c8b5aa8adeeaed40fbceda6d`에는 Gradle Wrapper와 iOS 버전 메타데이터가 포함됩니다. 모든 플랫폼 job이 성공했고 Android 디버그 APK와 iOS 시뮬레이터 앱을 생성했습니다. 이후 변경은 이 검증 기록·README 링크 정리와 wrapper 스크립트 끝의 빈 줄 정리뿐입니다.

## Android 1.1.0 로컬 변경 검증 (2026-11-06)

`.env` 빌드 인증, 가습기/공기청정기/공기청정 팬, 여러 기기 선택과 위젯별 최대 2대 설정, 1×1/1×2/2×1 레이아웃, 카드 대시보드를 추가했습니다. iOS 구현은 변경하지 않았습니다.

완료한 확인:

- `:core:check :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` 성공. 코어 3,146 assertions, HTTP 어댑터 단위 테스트 성공. lint 0 errors / 28 warnings (주로 동기 SharedPreferences 저장, 버전별 위젯 속성과 기존 의존성 버전 안내).
- 실제 PAT로 읽기 전용 기기 목록/상태 조회: 지원 유형 3대 발견. 공기청정 팬과 가습기의 온도·습도 정상 수신. 일반 공기청정기는 HTTP 416 / API 1222(오프라인). 개인 기기 ID, PAT, 원본 응답은 기록하지 않았습니다.
- `.env`는 Git 제외 / 권한 600. 소스 및 Git에 표시되는 파일에 실제 PAT가 없는지 확인. APK에는 의도한 내장 인증이 들어가며 `.env` 자체를 asset으로 복사하지 않습니다.
- `git diff --check` 통과.

삼성 SM-S926N / Android 16 실기기 확인 완료:

- 새 APK 1.1.0 설치 성공. PAT 입력 없이 자동 연결하고 공기청정 팬/가습기의 온도·습도와 수신 시각이 각각 표시되는 것을 화면과 UI 트리에서 확인했습니다. 대시보드 시각적 확인 완료.
- `StoreTest` 9개 통과: Keystore, 기존 데이터 이전, 기기별 캐시/요청 시각, 설정 세대, 위젯 선택/복원과 내장 인증. 실제 사용자 계정 데이터는 별도 테스트 저장소로 보호했습니다.
- `WidgetLayoutTest` 3개 통과: 실제 설치된 1×1/1×2 메타데이터와 크기 조절 속성, RemoteViews 56dp/72dp 한 셀 레이아웃, 세로/가로 두 기기의 값 구분과 글자 높이/너비. 글자 높이 검사는 소수점 FontMetrics 대신 Android가 실제 사용하는 픽셀 단위 lineBottom으로 확인합니다.
- 총 **12 tests / 0 failures**. 실행 로그는 로컬 `/tmp/thinq-instrumentation.log`에 있습니다.

남은 수동 점검:

- Samsung 홈 화면에서 실제 위젯 추가/축소/확대와 위젯별 설정을 런처에서 조작하는 전체 흐름. 코드의 RemoteViews 및 위젯별 설정/복원은 실기기 계측 검사로 확인했으며, 실제 홈 화면 배치까지 확인했다고 해석하지 않습니다. 기존 위젯 크기는 런처에 남으므로 1×1을 새로 추가하는 경로도 확인해야 합니다.

계측 테스트 실행 예 (실제 설정을 지우지 않는 별도 테스트 저장소 사용):

```sh
adb install --user 0 -r android/app/build/outputs/apk/debug/app-debug.apk
adb install --user 0 -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument --user 0 -w dev.thinqclimate.widget.test/androidx.test.runner.AndroidJUnitRunner
```

## Android 1.1.1: 2×1 직접 추가 (2026-11-06)

- 위젯 목록에 **Climate · 2 × 1**을 별도 등록했습니다. 기본 두 열 / 한 행, 가로 미리보기 및 앱 내 추가 버튼을 제공합니다. 기존 기기별 설정과 크기 조절 동작을 공유하며 전체 갱신 대상에도 포함됩니다.
- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` 성공.
- 삼성 SM-S926N / Android 16에 1.1.1(versionCode 3) 업데이트 설치 성공. 실제 설치된 세 제공자의 셀 크기, 가로 두 기기의 레이아웃/값, 저장 동작을 포함한 계측 테스트 **12개 통과**. 로그: `/tmp/thinq-2x1-instrumentation.log`.
- 실제 홈 화면 배치 조작은 수행하지 않았습니다. 홈 화면 위젯 목록 또는 앱 하단의 **2 × 1 · 기기 2대 가로로**로 추가합니다.

## Android 1.2.0: 색상과 시각적 검증 (2026-11-06)

- 기존 삼성 홈 화면과 앱 하단을 캡처해 왼쪽 정렬, 작은 내부 여백, 길어진 수신 시각과 플랫폼 버튼 그림자를 확인했습니다. 위젯은 가운데 정렬/내부 여백/HH:mm 시각으로, 앱 버튼은 그림자 없는 동일 크기의 선택 카드로 수정했습니다.
- 위젯별 6가지 프리셋과 배경/습도 강조색 직접 입력, 배경 불투명도(40~100%), 실제 RemoteViews 미리보기를 추가했습니다. 적용 전에는 기존 위젯 설정을 저장하지 않습니다.
- 첫 1.2.0 APK 설치 성공. 실제 홈 화면의 2×1 위젯에 가운데 정렬과 아이보리 배경이 적용된 것을 캡처했습니다. 개인 배경화면이 담긴 홈 화면 이미지는 Git에서 제외한 `.test-output/design/`에만 보관합니다.
- 기능 계측 **15 tests / 0 failures**: 기존 저장/기기/위젯 검증, 위젯별 색상 저장/복원/독립성, 6개 프리셋 대비와 실제 렌더링, 시스템 글꼴 130%의 실제 삼성 셀 크기에서 글자 높이/너비 확인. 로그 `/tmp/thinq-design-tests.log`.
- 별도 `DesignCaptureTest`는 명시적으로 실행할 때만 앱과 꾸미기 화면의 뷰를 PNG로 덤프합니다. 잠금 화면을 해제하지 않고 앱 소유 뷰만 그리며, 미리보기의 프리셋 변경은 실제 설정에 적용하지 않습니다. 첫 캡처 검사 1회 통과.
- 최종 캡처 반복 중 USB가 끊겼습니다. 카드 글자 가운데 정렬/드롭다운 화살표 및 사용자 지정 중간 밝기 배경의 글자 대비 보완까지 빌드 성공했으며, 최종 APK 재설치/최종 이미지 회수/USB 화면 켜짐 설정 복원은 재연결 후 마무리합니다.

## Android/iOS 1.3.0 공개 배포 준비 (2026-11-06)

- Release/Dev 앱을 분리했습니다. Release의 PAT는 `.env`와 환경 변수에 실제 토큰이 있어도 항상 빈 값이며, 공개 앱 첫 실행은 사용자 토큰 입력 화면으로 이동합니다. 두 앱 모두 연결 설정에서 토큰을 교체할 수 있습니다.
- 새 토큰으로 기기 목록 조회가 성공한 뒤 저장합니다. 실패 시 기존 인증 유지, 성공 시 이전 센서 캐시 삭제와 세대 변경으로 늦은 결과를 차단합니다. Android 위젯 색상은 유지하며, 다른 계정의 기존 기기 연결은 자동으로 임의 기기로 바꾸지 않습니다.
- `:core:check :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest` 로컬 성공. 공개 빌드의 토큰 미포함을 검사하는 Release 단위 테스트와 APK 전체/생성 BuildConfig 검사를 추가했습니다.
- 실제 개인 `.env`가 있는 개발 PC에서 만든 서명된 공개 APK가 토큰 미포함 검사 및 apksigner 검증을 통과했습니다. 공개 서명 인증서 SHA-256: `bb3786225dda885586f2e70c563e73c3f5bb2824e89e6e4d7ea5d380058385cd`.
- Swift 코어 8개 테스트 통과, iOS Release 시뮬레이터 앱/위젯 빌드 성공. Apple 배포 인증서/프로파일/API 키가 아직 없어 실제 TestFlight 업로드와 iPhone 설치는 미실행입니다.
- GitHub Actions 3개 워크플로의 actionlint, iOS 업로드 스크립트 shellcheck, Python 스크립트 컴파일, `git diff --check` 통과.
- GitHub Releases 자동화는 버전 태그에서 서명된 APK/체크섬/인증서 정보/설치 안내를 게시합니다. iOS TestFlight 자동화는 Apple 설정과 명시적 활성화 전에는 실행하지 않습니다.
