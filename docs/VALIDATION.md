# 검증 기록

2026-11-06. 실제 실행과 준비된 자동화를 구분합니다.

| 검증 | 결과 | 근거 |
|---|---|---|
| Android 순수 Java 코어 | 통과 | JVM에서 3,139 assertions 실행 |
| 실제 ThinQ HTTP 어댑터 계약 | 통과 | org.json 20240303 소스로 컴파일, 모의 HTTP 연결 55 assertions 실행 |
| Android 리소스 XML | 통과 | Python XML 파서 전체 파일 확인 |
| Apple 개인정보 manifest | 통과 | plistlib 파서로 앱/확장 파일 확인 |
| 프로젝트/CI YAML | 통과 | YAML 파서 확인 |
| Android APK·lint·Gradle 단위 테스트 | CI 확인 필요 | Mobile CI android job |
| Android Keystore·캐시 계측 테스트 | CI 확인 필요 | Mobile CI android-device job (API 35 emulator) |
| Swift 테스트 및 iOS 앱/확장 빌드 | CI 확인 필요 | Mobile CI ios job (macOS) |
| LG 실제 PAT 및 가습기 센서 | 미실행 | 사용자 인증·모델이 제공되지 않음 |
| iPhone/Android 실제 홈 화면 위젯 | 미실행 | 실기기 및 iOS 서명 계정 필요 |

로컬 환경에는 JDK compiler 모듈은 있으나 javac 실행 파일/ct.sym이 없어 `java com.sun.tools.javac.Main -source 17 -target 17`을 사용했습니다. 표준 JDK 17 환경은 CI에서 `--release 17` 및 Gradle로 검증합니다. HTTP 계약 로컬 검증의 JSON 라이브러리는 GitHub 연결 도구를 통해 공식 JSON-java 20240303 소스를 임시 디렉터리에 받아 컴파일했으며 저장소에 외부 소스를 복사하지 않았습니다. 일반 재현 명령은 `cd android && gradle :core:check :app:testDebugUnitTest`입니다.

로컬에는 Android SDK·Gradle·Swift·Xcode가 없고 환경의 다운로드 프록시 연결이 거부되어 플랫폼 도구를 추가 설치할 수 없었습니다. 플랫폼 테스트 결과를 로컬 통과로 표현하지 않습니다. 실제 Actions 결과는 이 문서의 작성 시점과 다를 수 있으므로 저장소 Actions를 확인하세요.
