# SSH Terminal (MVP Scaffold)

Android native SSH terminal app scaffold focused on TTY-based remote CLI workflows.

## What is included

- Minimal Android Compose app module
- Foreground service skeleton for long-running SSH sessions
- Session state machine (`IDLE/CONNECTING/CONNECTED/FAILED`)
- SSH abstraction interfaces (`SshClient`, `SshSession`)
- `sshj`-based real SSH adapter with `PTY shell`, `window-change`, and stream read/write loop
- Host key change detection UX (fingerprint dialog with `Reject (Default)`, `Trust Once`, `Update known_hosts`)
- stdin 입력 UI (`Ctrl/Alt/ESC/TAB/ENTER`, 방향키, `^C/^D`, 실시간 키 입력 전송)
- SFTP 파일 브라우저: 영속 세션, 자동 디렉터리 리스팅, SAF 기반 업로드/다운로드(진행률 표시), mkdir/삭제/이름변경, 롱프레스 컨텍스트 메뉴
- Android 14+/targetSdk 35 foreground service 권한 정리 (`FOREGROUND_SERVICE_DATA_SYNC`)
- 터미널 렌더링 개선: Nerd Font 번들 적용 + truecolor(24-bit) 색상 처리
- GitHub Actions CI: `assembleDebug` + `testDebugUnitTest`
- CI artifacts: debug APK + unit test reports
- Placeholder key repository interface for Keystore+AEAD implementation

- 한글/CJK 입력 정상 처리 (IME composition 인식) + 전각 문자 렌더링
- 터미널 스크롤백 (드래그로 이전 출력 확인, 2000줄 버퍼) + PgUp/PgDn 버튼으로 페이지 단위 로컬 스크롤
- Terminal bell 알림: BEL 수신 시 Android 알림 (디바운스 5초, 탭별 독립)

## Current scope

SSH 터미널 + SFTP 파일 관리가 동작하는 상태. `sshj` 기반.

## Code convention

- Team convention: `docs/code-convention.md`
- Editor baseline: `.editorconfig`

Recent refactor baseline:

- `ConnectionProfile -> ConnectRequest` 생성 경로를 공용 팩토리로 통일
- SSH/SFTP의 `sshj` 인증 로직을 공용 함수로 통일
- `known_hosts` 파일 생성/업데이트 로직을 공용 유틸로 통일

## Build & test (CLI, WSL 기준)

This repository now includes Gradle Wrapper (`gradlew`).

1. Prepare environment variables:

```bash
export JAVA_HOME=/home/h1655/Dev/android-ssh/.local/jdk/jdk-17.0.14+7
export ANDROID_SDK_ROOT=/home/h1655/Dev/android-ssh/.local/android-sdk
export GRADLE_USER_HOME=/home/h1655/Dev/android-ssh/.gradle-local
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
```

2. Build debug APK:

```bash
./gradlew assembleDebug
```

3. Run unit tests:

```bash
./gradlew --no-daemon testDebugUnitTest
```

4. Run style/quality checks:

```bash
./gradlew --no-daemon ktlintCheck detekt
```

5. (Optional) Run connected Android tests:

```bash
./gradlew --no-daemon connectedDebugAndroidTest
```

### Automation scripts

- CI-equivalent local check:

```bash
./scripts/ci-check.sh
```

- Physical-device smoke run (unit test + install + launch):

```bash
./scripts/device-smoke.sh
```

- If you only want install + launch on a connected device:

```bash
./scripts/device-smoke.sh --skip-tests
```

- Security-key(OpenSSH `sk-ecdsa`) 실기기 E2E 서버 준비/로그 확인:

```bash
./scripts/security-key-e2e.sh start
./scripts/security-key-e2e.sh status
./scripts/security-key-e2e.sh show-log
./scripts/security-key-e2e.sh stop
```

- If multiple devices are connected, select one with `ANDROID_SERIAL`:

```bash
ANDROID_SERIAL=<device-id> ./scripts/device-smoke.sh
```

## Release build for Google Play

1. Bump release version in `app/build.gradle.kts`:

- `versionCode` must be higher than the previous Play release
- `versionName` should match your release label

2. Prepare upload signing config (do not commit secrets):

```bash
cp keystore.properties.example keystore.properties
```

Fill `keystore.properties`:

```properties
storeFile=/absolute/path/to/upload-keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

You can use env vars instead of file:

- `ANDROID_UPLOAD_STORE_FILE`
- `ANDROID_UPLOAD_STORE_PASSWORD`
- `ANDROID_UPLOAD_KEY_ALIAS`
- `ANDROID_UPLOAD_KEY_PASSWORD`

3. Build AAB:

```bash
./gradlew --no-daemon :app:bundleRelease
```

Output:

- `app/build/outputs/bundle/release/app-release.aab`

4. Upload `app-release.aab` to Play Console (`Internal testing` or `Production` track).

Output APK path:

- `app/build/outputs/apk/debug/app-debug.apk`

Detekt baseline path:

- `app/detekt-baseline.xml`

## Run on Android device

1. (WSL2) Windows PowerShell에서 폰을 WSL로 attach:

```powershell
usbipd bind --busid <BUSID>
usbipd attach --wsl --busid <BUSID>
```

2. WSL에서 ADB 연결 확인 (`device` 상태여야 함):

```bash
adb devices -l
```

3. 디버그 앱 설치:

```bash
./gradlew --no-daemon installDebug
```

4. 앱 실행:

```bash
adb shell am start --user current -n com.opencode.sshterminal/.app.MainActivity
```

If `unauthorized` appears, approve the RSA debugging prompt on the phone.
If `no permissions` appears, add a udev rule for your vendor ID and reload rules.

## Data note

- Saved connection profiles are encrypted with Android Keystore-backed AES-GCM.
- Legacy plaintext profile payloads are not loaded.

## Next implementation order

1. Replace custom Compose renderer with `terminal-view` integration (or `libvterm`-based engine)
2. Implement `KeyRepository` with Android Keystore-backed AEAD encryption
3. SFTP 개선: pull-to-refresh (Compose BOM 업그레이드 필요), 재귀 디렉터리 삭제, 다중 파일 선택

## Important paths

- `app/src/main/java/com/opencode/sshterminal/session/SessionManager.kt`
- `app/src/main/java/com/opencode/sshterminal/ssh/SshClient.kt`
- `app/src/main/java/com/opencode/sshterminal/terminal/TermuxTerminalBridge.kt`
- `app/src/main/java/com/opencode/sshterminal/service/SshForegroundService.kt`
- `app/src/main/java/com/opencode/sshterminal/sftp/SftpChannelAdapter.kt`
- `app/src/main/java/com/opencode/sshterminal/sftp/SshjSftpAdapter.kt`
- `app/src/main/java/com/opencode/sshterminal/ui/sftp/SftpBrowserViewModel.kt`
- `app/src/main/java/com/opencode/sshterminal/ui/sftp/SftpBrowserScreen.kt`
- `app/src/main/java/com/opencode/sshterminal/service/BellNotifier.kt`
- `app/src/test/java/com/opencode/sshterminal/sftp/SshjSftpAdapterTest.kt`
- `app/src/test/java/com/opencode/sshterminal/ui/terminal/TerminalScrollTest.kt`
