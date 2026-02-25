<p align="center">
  <img src="docs/assets/branding/AndSSH.png" alt="AndSSH" width="128" />
</p>

<h1 align="center">AndSSH</h1>

<p align="center">
  A free, open-source Android SSH terminal &mdash; no subscriptions, no cloud, fully local.
</p>

<p align="center">
  <a href="https://github.com/jy1655/android-ssh/actions"><img src="https://github.com/jy1655/android-ssh/actions/workflows/android-ci.yml/badge.svg" alt="CI" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="License: MIT" /></a>
  <img src="https://img.shields.io/badge/platform-Android%2026%2B-green.svg" alt="Platform" />
</p>

---

## Why AndSSH?

Most mobile SSH apps are either abandoned, cloud-dependent, or locked behind subscriptions.
AndSSH is built for developers and sysadmins who want a **capable, secure, and private** SSH terminal on Android — with everything stored on-device.

## Features

### Terminal

- Full terminal emulation via [Termux terminal-view](https://github.com/termux/termux-app) (xterm-256color, truecolor 24-bit)
- Unicode, CJK, and Korean IME composition support
- Scrollback buffer with page-up/page-down navigation
- Terminal bell notifications (per-tab, debounced)
- Split-view: run two terminal panes side by side
- Customizable color schemes per connection (Solarized, Dracula, Nord, and more)
- Adjustable font size with pinch-to-zoom
- Configurable cursor style (block / underline / bar)
- Haptic feedback on key press
- Command snippet library with quick search and run
- Local command history

### SSH & Networking

- SSH-2 via [sshj](https://github.com/hierynomus/sshj) with BouncyCastle provider
- Password, public key (RSA / Ed25519 / ECDSA), and certificate-based authentication
- In-app SSH key generation
- ProxyJump / jump-host chaining
- Port forwarding — local, remote, and dynamic SOCKS
- Port knocking (per-connection sequence + delay)
- Keepalive interval configuration
- SSH compression toggle
- Mosh protocol support with automatic SSH fallback
- SSH Agent forwarding
- Auto-reconnect on network change

### SFTP

- Built-in file browser with directory listing, upload, download (progress bar)
- Create, rename, delete files and directories (including recursive delete)
- Multi-file selection with bulk operations
- Pull-to-refresh
- Permission editing (chmod)
- SAF (Storage Access Framework) integration

### Connection Management

- Encrypted connection profiles (AES-GCM backed by Android Keystore)
- Quick Connect for one-off sessions
- Groups, tags, and search/filter
- SSH config import (`~/.ssh/config`)
- Encrypted local backup and restore
- Per-connection startup commands
- Per-connection environment variables

### Security

- **App lock** — master password and/or biometric (fingerprint, face) with device-bound Keystore key
- **Auto-lock timeout** — configurable inactivity lock
- **Clipboard protection** — auto-clear timer + Android 13+ sensitive content flag
- **Screenshot prevention** — optional `FLAG_SECURE` toggle
- **In-memory zeroization** — passwords, keys, and sensitive buffers are zeroed after use
- **Strict host key checking** — fingerprint verification with reject-by-default policy

### Customization

- Customizable hardware keyboard bindings
- Configurable virtual key row (Ctrl, Alt, ESC, Tab, arrows, function keys)
- Dark / light theme support
- Nerd Font bundled; additional font options
- Terminal workspace restoration on launch

## Known Limitations

- **Security key enrollment** is disabled in this release. The U2F-based enrollment flow was rejected by the device/Play Services combination across all tested `appId` candidates. Existing enrolled security key connections continue to work. A FIDO2-based replacement is tracked as a future milestone.
- SSH key agent forwarding is limited to the active session lifetime.

## Getting Started

### Prerequisites

| Requirement | Version |
|---|---|
| JDK | 17 |
| Android SDK | API 35 (compileSdk) |
| Gradle | Wrapper included (`./gradlew`) |

> **Tip**: [Android Studio](https://developer.android.com/studio) includes the JDK and SDK. For CLI-only builds, install JDK 17 separately and use `sdkmanager` to fetch the SDK.

### Build

```bash
# Debug APK
./gradlew assembleDebug

# Unit tests
./gradlew testDebugUnitTest

# Style and quality checks
./gradlew ktlintCheck detekt
```

Or run the CI-equivalent script:

```bash
./scripts/ci-check.sh
```

### Install on a Connected Device

```bash
./gradlew installDebug
adb shell am start -n com.opencode.sshterminal/.app.MainActivity
```

A convenience script is also available:

```bash
./scripts/device-smoke.sh          # test + install + launch
./scripts/device-smoke.sh --skip-tests  # install + launch only
```

## Release Build (Google Play)

### Automatic upload on push (recommended)

When you push to `main`, the GitHub Actions `Play Release` workflow builds an AAB and uploads it to Google Play.

1. Add repository secrets:
   - `PLAY_SERVICE_ACCOUNT_JSON` (Google Play service account JSON, raw content)
   - `ANDROID_UPLOAD_KEYSTORE_BASE64` (`upload-keystore.jks` base64)
   - `ANDROID_UPLOAD_STORE_PASSWORD`
   - `ANDROID_UPLOAD_KEY_ALIAS`
   - `ANDROID_UPLOAD_KEY_PASSWORD`

2. Optional repository variables:
   - `PLAY_TRACK` (default: `internal`)
   - `PLAY_RELEASE_STATUS` (default: `completed`)
   - `PLAY_VERSION_CODE_OFFSET` (default: `100000`)

3. Push to `main`.
   - workflow file: `.github/workflows/play-release.yml`
   - CI computes version code as `PLAY_VERSION_CODE_OFFSET + GITHUB_RUN_NUMBER` to avoid duplicate version-code upload failures.

### Manual upload

1. **Bump version** in `app/build.gradle.kts` — increment `versionCode`, set `versionName`.

2. **Prepare signing config** (do not commit secrets):

   ```bash
   cp keystore.properties.example keystore.properties
   ```

   Fill in `keystore.properties`:

   ```properties
   storeFile=/absolute/path/to/upload-keystore.jks
   storePassword=...
   keyAlias=...
   keyPassword=...
   ```

   Alternatively, export environment variables:

   ```
   ANDROID_UPLOAD_STORE_FILE
   ANDROID_UPLOAD_STORE_PASSWORD
   ANDROID_UPLOAD_KEY_ALIAS
   ANDROID_UPLOAD_KEY_PASSWORD
   ```

3. **Build AAB**:

   ```bash
   ./gradlew :app:bundleRelease
   ```

   Output: `app/build/outputs/bundle/release/app-release.aab`

4. Upload the `.aab` to [Google Play Console](https://play.google.com/console).

## Project Structure

```
app/src/main/java/com/opencode/sshterminal/
├── app/          # Application, MainActivity, Hilt setup
├── data/         # Persistence models and repositories
├── security/     # Encryption, key management, biometric, U2F
├── service/      # Foreground service, bell notifier
├── session/      # Session and tab state machine
├── sftp/         # SFTP protocol adapter (sshj)
├── ssh/          # SSH protocol adapter (sshj)
└── ui/           # Jetpack Compose screens and ViewModels
```

## Code Convention

See [`docs/code-convention.md`](docs/code-convention.md) for the team style guide. Baseline tooling:

- **ktlint** — code style
- **detekt** — static analysis (baseline: `app/detekt-baseline.xml`)
- **EditorConfig** — `.editorconfig` at repo root

## Contributing

Contributions are welcome. Please read [`CONTRIBUTING.md`](CONTRIBUTING.md) before opening a pull request.

## Security

If you discover a vulnerability, **do not open a public issue**. Follow the process in [`SECURITY.md`](SECURITY.md) — preferably via [GitHub Security Advisories](https://github.com/jy1655/android-ssh/security/advisories/new).

## Privacy Policy

- Source: [`docs/privacy-policy.md`](docs/privacy-policy.md)
- Public URL for Google Play Console:
  - https://github.com/jy1655/android-ssh/blob/main/docs/privacy-policy.md

## License

This project is licensed under the [MIT License](LICENSE).

```
MIT License — Copyright (c) 2026 AndSSH contributors
```
