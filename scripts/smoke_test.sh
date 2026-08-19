#!/usr/bin/env bash
set -Eeuo pipefail

SOURCE_APK="apk/DraftWA_Mobile_Drafts_v0.6.1_SAFE.apk"
PATCHED_APK="apk/DraftWA_Mobile_Drafts_v0.6.2_DEXFIX_CI.apk"
PACKAGE="com.draftwa.mobile"
EXPECTED_SOURCE_SHA256="106ab80a592e80649c983707b08785c9806db25ed5b1ed87c999c6cc748a4df3"
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

echo "== Reconstruct exact v0.6.1 source APK =="
PARTS=(apk/payload/part_*.b64)
if [[ ! -e "${PARTS[0]}" ]]; then
  echo "APK payload parts are missing." >&2
  exit 2
fi
cat "${PARTS[@]}" | tr -d '\r\n' | base64 --decode > "$SOURCE_APK"

echo "$EXPECTED_SOURCE_SHA256  $SOURCE_APK" | sha256sum -c - | tee "$OUT/source-sha256-check.txt"
stat -c 'source_bytes=%s' "$SOURCE_APK" | tee "$OUT/source-apk-size.txt"

echo "== Patch invalid classes2.dex code_item outs_size =="
python3 scripts/patch_dex_outs.py "$SOURCE_APK" "$PATCHED_APK" | tee "$OUT/dex-patch.txt"

echo "== Sign patched APK with an ephemeral CI key =="
CI_KEYSTORE="$RUNNER_TEMP/draftwa-ci.p12"
CI_PASSWORD='DraftWA-CI-2026!'
keytool -genkeypair -noprompt \
  -keystore "$CI_KEYSTORE" \
  -storetype PKCS12 \
  -storepass "$CI_PASSWORD" \
  -keypass "$CI_PASSWORD" \
  -alias draftwa_ci \
  -keyalg RSA -keysize 3072 -validity 3650 \
  -dname 'CN=DraftWA CI Test, OU=CI, O=DraftWA, L=Lome, ST=Maritime, C=TG' \
  > "$OUT/keytool.txt" 2>&1
jarsigner \
  -keystore "$CI_KEYSTORE" \
  -storepass "$CI_PASSWORD" \
  -keypass "$CI_PASSWORD" \
  -sigalg SHA256withRSA \
  -digestalg SHA-256 \
  "$PATCHED_APK" draftwa_ci \
  > "$OUT/jarsigner.txt" 2>&1
jarsigner -verify "$PATCHED_APK" > "$OUT/jarsigner-verify.txt" 2>&1
sha256sum "$PATCHED_APK" | tee "$OUT/patched-sha256.txt"
stat -c 'patched_bytes=%s' "$PATCHED_APK" | tee "$OUT/patched-apk-size.txt"

echo "== Device =="
adb wait-for-device
adb shell getprop ro.build.version.release | tee "$OUT/android-release.txt"
adb shell getprop ro.build.version.sdk | tee "$OUT/android-sdk.txt"

echo "== Install patched APK =="
adb install -r "$PATCHED_APK" | tee "$OUT/install.txt"

echo "== Clear logcat and launch =="
adb logcat -c
adb shell am force-stop "$PACKAGE" || true
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 | tee "$OUT/launch.txt"

sleep 10

cleanup_and_collect
trap - EXIT

PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
echo "pid=$PID" | tee "$OUT/process.txt"

if grep -A100 -B15 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -Fq "Process: $PACKAGE"; then
  echo "FAIL: DraftWA produced a FATAL EXCEPTION." >&2
  grep -A100 -B15 "FATAL EXCEPTION" "$OUT/logcat.txt" > "$OUT/fatal-exception.txt" || true
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
