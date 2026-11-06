# GitHub 다운로드 배포

## 사용자 다운로드 주소

- Android: https://github.com/seyongida/lgthinq-climate-widget/releases/latest/download/ThinQ-Climate.apk
- 버전별 파일/체크섬: https://github.com/seyongida/lgthinq-climate-widget/releases
- iOS: TestFlight 서명/심사/공개 초대 링크 설정 후 릴리즈 설명에 연결합니다. 현재 실제 공개 초대 링크는 등록하지 않았습니다.

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
git tag v1.3.0
git push origin v1.3.0
```

`Publish Android release`는 코어/Release 단위 테스트/lint → APK 빌드 → 공개 자격 증명/버전/서명 검사 → GitHub Releases 게시 순서로 실행합니다. 게시 파일은 `ThinQ-Climate.apk`, `SHA256SUMS`, `SIGNING_CERTIFICATE.txt`, `INSTALL.md`입니다. 같은 릴리즈를 덮어쓰지 않으며 수정은 새 버전 태그로 배포합니다.

`Mobile CI`의 Debug APK는 검사/개발용이며, 일반 사용자는 Releases의 공개 APK를 사용합니다. CI에는 `.env`가 없으므로 Debug 산출물에도 개발자 토큰이 없습니다.

## iOS 배포 준비

일반 iPhone에 APK처럼 IPA 파일만 다운로드해 설치시키는 방식은 사용할 수 없습니다. 이 프로젝트의 공개 배포 경로는 **TestFlight 공개 초대 링크**입니다. Apple Developer Program, App Store Connect 앱 등록, 두 Bundle ID와 App Group, App Store 배포 인증서와 두 프로비저닝 프로파일이 필요합니다. 외부 테스트는 Apple의 TestFlight 심사와 공개 그룹/초대 링크 설정도 필요합니다.

이 환경에서 확인한 Apple 인증서는 개발용뿐이므로 배포 인증서/API 키를 임의로 대신 사용하거나 서명되지 않은 IPA를 설치 파일로 공개하지 않습니다. 다음 값을 GitHub 저장소 Settings → Secrets and variables → Actions에 설정합니다. 비밀 값은 채팅/이슈에 올리지 마세요.

Variables:

| Variable | 값 |
|---|---|
| `IOS_TEAM_ID` | Apple Developer Team ID |
| `IOS_BUNDLE_ID` | 등록한 앱 Bundle ID, 기본 `dev.thinqclimate.widget` |
| `IOS_APP_GROUP` | 두 타깃에 등록한 App Group, 기본 `group.dev.thinqclimate.widget` |
| `IOS_TESTFLIGHT_URL` | 실제 생성한 `https://testflight.apple.com/join/...` 공개 초대 링크 |
| `IOS_TESTFLIGHT_ENABLED` | 태그 릴리즈 후 자동 업로드할 때만 `true` |
| `IOS_BUILD_NUMBER` | 자동 업로드에서 지정할 빌드 번호(선택). 없으면 릴리즈 워크플로 실행 번호 사용 |

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
