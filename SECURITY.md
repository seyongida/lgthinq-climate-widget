# 보안과 개인정보

사용자 PAT를 저장소·이슈·로그에 넣지 마세요. 앱은 LG 로그인 비밀번호를 수집하지 않고 LG 공식 PAT를 사용합니다. PAT는 기기 목록과 상태 읽기에 필요한 최소 권한으로 발급하세요.

iOS는 앱/위젯 공유 Keychain의 `AfterFirstUnlockThisDeviceOnly` 항목에 인증을 저장합니다. 센서 캐시와 기기 이름은 App Group 사용자 기본 설정에 저장합니다. 캐시에는 PAT가 없습니다. 두 프로세스의 네트워크 갱신/설정 변경은 파일 잠금으로 보호합니다.

Android는 Android Keystore의 내보낼 수 없는 AES 키와 GCM으로 PAT 설정을 암호화합니다. 백업을 끄고 수동 PAT 입력 대화상자의 화면 캡처와 입력 필드 자동 저장/자동 완성을 제한했습니다. 센서 대시보드에는 PAT를 표시하지 않습니다. 앱의 비공개 저장소에 센서값·기기 이름·마지막 시각을 보관합니다. 루팅/탈옥, 악성 접근성 서비스, OS 자체 침해까지 방어하는 것은 아닙니다.

별도 분석 SDK, 광고, 앱 서버, 외부 오류 수집이 없습니다. HTTPS로 LG 지역별 고정 호스트에만 API 요청을 보내며 토큰을 리다이렉트 대상에 전달하지 않습니다. 공개 SDK의 클라이언트 키는 코드에 있지만 사용자 PAT가 아닙니다.

연결 해제는 로컬 인증과 센서 캐시를 지웁니다. PAT의 서버 측 폐기는 LG 발급 페이지에서 수행하세요. 취약점을 제보할 때 실제 자격 증명이나 개인 기기 응답을 포함하지 마세요.

## Android 개인 설치용 내장 PAT

루트 `.env`의 `THINQ_PAT`, `THINQ_COUNTRY`, `THINQ_REGION` 또는 같은 이름의 환경 변수로 빌드 시 계정을 지정할 수 있습니다. `.env`는 Git에서 제외하며 `.env.example`에는 토큰을 넣지 않습니다. Gradle은 BuildConfig에 값을 넣고 앱은 첫 실행 때 Keystore 저장소로 가져옵니다. 다음 빌드에서 자격 증명이 바뀌면 기존 인증을 교체합니다.

**Keystore 저장은 APK 안에 포함된 PAT를 숨기지 못합니다. APK에서 PAT 추출이 가능하므로 이 방식은 개인 설치용입니다.** 실제 PAT를 포함한 APK, 테스트 APK, BuildConfig와 빌드 산출물을 공개하거나 CI 아티팩트로 배포하지 마세요. 공개 CI에는 개인 PAT를 전달하지 마세요. PAT를 폐기하려면 LG 발급 페이지에서 취소하고 `.env`를 수정한 뒤 다시 빌드/설치하세요.

## 공개 Release와 사용자 토큰

Android Release는 `.env`/`THINQ_PAT` 환경 변수 값과 관계없이 `BuildConfig.THINQ_PAT`를 빈 값으로 고정하고 bootstrap도 Debug에서만 허용합니다. 공개 앱 ID를 `.release`로 분리해 개인 Dev 앱의 인증/서명과 섞이지 않게 합니다. 공개 APK는 배포 서명, 버전 일치, 자격 증명 미포함 검사를 통과해야 GitHub Releases에 게시됩니다.

공개/Dev 모두 연결 설정에서 토큰을 바꿀 수 있습니다. 새 인증으로 기기 목록을 읽을 수 있는지 확인하기 전에는 기존 인증을 바꾸지 않습니다. 성공 시 이전 센서 캐시를 지우고 설정 세대를 바꿔 이전 요청의 늦은 결과를 거부합니다. iOS도 Keychain 인증을 저장하고 앱/위젯 잠금 아래에서 기존 센서 캐시를 지웁니다. 공개 앱은 입력한 토큰을 빌드 시스템이나 GitHub로 보내지 않습니다.

배포용 Android 키는 `.env.signing`과 `android/keystore/release.jks`에만 로컬 보관하고 CI에는 GitHub Actions Secrets로 주입합니다. Apple 배포 인증서와 API 키도 Secrets로만 전달합니다. 서명 자료는 공개 소스/릴리즈 파일에 포함하지 않습니다. [배포 운영 안내](docs/DISTRIBUTION.md)의 키 백업과 Secret 이름을 참고하세요.
