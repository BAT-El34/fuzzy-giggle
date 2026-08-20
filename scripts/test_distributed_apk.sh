#!/usr/bin/env bash
set -euo pipefail

API_LEVEL="${API_LEVEL:-unknown}"
PACKAGE="com.draftwa.mobile"
EXPECTED_SHA256="9cf57d47794927760995d6bd75b8f2b55a4aa3c220ceb5fffca88f063dae43d8"
OUT="artifacts/distributed-api-${API_LEVEL}"
APK="$OUT/DraftWA-distributed.apk"

mkdir -p "$OUT"

echo "[1/8] Reconstruct exact APK bytes currently installed by the user"
cat preproduction/current-apk/part_*.b64 | tr -d '\r\n' | base64 --decode > "$APK"
ACTUAL_SHA256="$(sha256sum "$APK" | awk '{print $1}')"
SIZE="$(stat -c %s "$APK")"
echo "$ACTUAL_SHA256  $APK" | tee "$OUT/sha256.txt"
echo "$SIZE" | tee "$OUT/size.txt"
if [[ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]]; then
  echo "Unexpected APK hash: $ACTUAL_SHA256" | tee "$OUT/failure.txt"
  exit 20
fi
if [[ "$SIZE" != "51654" ]]; then
  echo "Unexpected APK size: $SIZE" | tee "$OUT/failure.txt"
  exit 20
fi

unzip -l "$APK" > "$OUT/apk-contents.txt" || true

echo "[2/8] Install APK"
adb install -r "$APK" 2>&1 | tee "$OUT/adb-install.txt"
adb shell dumpsys package "$PACKAGE" > "$OUT/dumpsys-package.txt" || true
adb shell cmd package resolve-activity --brief "$PACKAGE" > "$OUT/resolve-activity.txt" 2>&1 || true

echo "[3/8] Clear logcat and launch through launcher intent"
adb logcat -c
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 > "$OUT/monkey.txt" 2>&1 || true
sleep 10

echo "[4/8] Capture process and foreground activity"
adb shell pidof "$PACKAGE" > "$OUT/pid.txt" 2>&1 || true
adb shell dumpsys activity activities > "$OUT/dumpsys-activity.txt" 2>&1 || true
adb shell dumpsys window windows > "$OUT/dumpsys-window.txt" 2>&1 || true

echo "[5/8] Capture Android runtime logs"
adb logcat -d -v threadtime > "$OUT/logcat.txt" 2>&1 || true
grep -E -n "FATAL EXCEPTION|AndroidRuntime|VerifyError|NoSuchMethodError|NoSuchFieldError|ClassNotFoundException|IncompatibleClassChangeError|IllegalAccessError|RuntimeException|Process: com\.draftwa\.mobile" "$OUT/logcat.txt" > "$OUT/crash-lines.txt" || true

echo "[6/8] Capture screenshot and UI tree"
adb exec-out screencap -p > "$OUT/screenshot.png" 2>/dev/null || true
adb shell uiautomator dump /sdcard/window.xml > "$OUT/uiautomator-command.txt" 2>&1 || true
adb pull /sdcard/window.xml "$OUT/window.xml" > "$OUT/uiautomator-pull.txt" 2>&1 || true

echo "[7/8] Evaluate startup"
PID="$(tr -d '\r\n ' < "$OUT/pid.txt" 2>/dev/null || true)"
if grep -qE "FATAL EXCEPTION|Process: com\.draftwa\.mobile|VerifyError|NoSuchMethodError|NoSuchFieldError|ClassNotFoundException|IncompatibleClassChangeError|IllegalAccessError" "$OUT/logcat.txt"; then
  echo "FAIL: crash signature detected" | tee "$OUT/result.txt"
  cat "$OUT/crash-lines.txt" || true
  exit 21
fi

if [[ -z "$PID" ]]; then
  echo "FAIL: DraftWA process is not alive 10 seconds after launch" | tee "$OUT/result.txt"
  grep -E -n "com\.draftwa\.mobile|AndroidRuntime|FATAL EXCEPTION|VerifyError|Exception" "$OUT/logcat.txt" | tail -n 300 || true
  exit 22
fi

echo "[8/8] PASS"
echo "PASS: process alive as PID $PID and no fatal signature detected" | tee "$OUT/result.txt"
