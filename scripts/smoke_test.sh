#!/usr/bin/env bash
set -Eeuo pipefail

APK_PATH="apk/DraftWA_Mobile_Drafts_v0.6.1_SAFE.apk"
PACKAGE="com.draftwa.mobile"
EXPECTED_SHA256="106ab80a592e80649c983707b08785c9806db25ed5b1ed87c999c6cc748a4df3"
API_LABEL="${API_LEVEL:-unknown}"
OUT="artifacts/api-${API_LABEL}"
mkdir -p "$OUT" apk

cleanup_and_collect() {
  adb logcat -d -v threadtime > "$OUT/logcat.txt" 2>/dev/null || true
  adb shell dumpsys activity activities > "$OUT/dumpsys-activity.txt" 2>/dev/null || true
  adb shell dumpsys package "$PACKAGE" > "$OUT/dumpsys-package.txt" 2>/dev/null || true
  adb exec-out screencap -p > "$OUT/screenshot.png" 2>/dev/null || true
}
trap cleanup_and_collect EXIT

echo "== Reconstruct APK from exact repository payload =="
PARTS=(apk/payload/part_*.b64)
if [[ ! -e "${PARTS[0]}" ]]; then
  echo "APK payload parts are missing." >&2
  exit 2
fi
cat "${PARTS[@]}" | tr -d '\r\n' | base64 --decode > "$APK_PATH"

echo "== Device =="
adb wait-for-device
adb shell getprop ro.build.version.release | tee "$OUT/android-release.txt"
adb shell getprop ro.build.version.sdk | tee "$OUT/android-sdk.txt"

echo "== APK integrity =="
echo "$EXPECTED_SHA256  $APK_PATH" | sha256sum -c - | tee "$OUT/sha256-check.txt"
stat -c 'bytes=%s' "$APK_PATH" | tee "$OUT/apk-size.txt"

echo "== Install =="
adb install -r "$APK_PATH" | tee "$OUT/install.txt"

echo "== Clear logcat and launch =="
adb logcat -c
adb shell am force-stop "$PACKAGE" || true
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 | tee "$OUT/launch.txt"

sleep 10

cleanup_and_collect
trap - EXIT

PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
echo "pid=$PID" | tee "$OUT/process.txt"

if grep -A80 -B10 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -Fq "Process: $PACKAGE"; then
  echo "FAIL: DraftWA produced a FATAL EXCEPTION." >&2
  grep -A80 -B10 "FATAL EXCEPTION" "$OUT/logcat.txt" > "$OUT/fatal-exception.txt" || true
  exit 1
fi

if grep -Eq "Force finishing activity .*${PACKAGE}|Process ${PACKAGE} .* has died" "$OUT/logcat.txt"; then
  echo "FAIL: Android reports that DraftWA died after launch." >&2
  grep -E "Force finishing activity .*${PACKAGE}|Process ${PACKAGE} .* has died" "$OUT/logcat.txt" > "$OUT/process-death.txt" || true
  exit 1
fi

if [[ -z "$PID" ]]; then
  echo "FAIL: DraftWA process is not alive 10 seconds after launch." >&2
  exit 1
fi

echo "PASS: DraftWA remained alive and no DraftWA FATAL EXCEPTION was detected."
