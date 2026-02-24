#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

RUN_TESTS=1
if [[ "${1:-}" == "--skip-tests" ]]; then
  RUN_TESTS=0
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb command not found. Add Android platform-tools to PATH."
  exit 1
fi

DEVICE_SERIAL="${ANDROID_SERIAL:-$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')}"
if [[ -z "$DEVICE_SERIAL" ]]; then
  echo "No authorized Android device found."
  adb devices
  exit 1
fi

ADB=(adb -s "$DEVICE_SERIAL")
if [[ "$("${ADB[@]}" get-state 2>/dev/null)" != "device" ]]; then
  echo "Selected device is not ready: $DEVICE_SERIAL"
  exit 1
fi

if [[ "$RUN_TESTS" -eq 1 ]]; then
  ./gradlew --no-daemon :app:testDebugUnitTest
fi

./gradlew --no-daemon :app:assembleDebug
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK_PATH" ]]; then
  echo "Debug APK not found at $APK_PATH"
  exit 1
fi
"${ADB[@]}" install -r "$APK_PATH" >/dev/null
"${ADB[@]}" shell am start --user current -n com.opencode.sshterminal/.app.MainActivity >/dev/null

echo "Device smoke run complete on $DEVICE_SERIAL: app installed and launched."
