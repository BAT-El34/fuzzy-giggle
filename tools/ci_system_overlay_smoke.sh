#!/usr/bin/env bash
set -euxo pipefail

API_LEVEL="${1:?API level required}"
mkdir -p artifacts

# Grant the special draw-over-apps AppOp exactly as a user would through the
# Android settings screen, then launch the 0.8.6 permission gate. The existing
# accessibility overlay must hand off to the independent foreground service.
adb shell appops set com.draftwa.mobile SYSTEM_ALERT_WINDOW allow \
  || adb shell cmd appops set com.draftwa.mobile SYSTEM_ALERT_WINDOW allow
adb shell appops get com.draftwa.mobile SYSTEM_ALERT_WINDOW > artifacts/system-overlay-appop.txt 2>&1 || true

grep -q 'SYSTEM_ALERT_WINDOW: allow' artifacts/system-overlay-appop.txt
adb shell am start -W -n com.draftwa.mobile/.OverlayGateActivity > artifacts/system-overlay-gate-start.txt
sleep 3

adb logcat -d -v threadtime > artifacts/system-overlay-start-logcat.txt
grep -q 'SYSTEM_OVERLAY_PERMISSION_OK' artifacts/system-overlay-start-logcat.txt
grep -q 'SYSTEM_OVERLAY_SHOWN' artifacts/system-overlay-start-logcat.txt
adb shell dumpsys activity services com.draftwa.mobile > artifacts/system-overlay-services.txt || true
grep -q 'DraftOverlayService' artifacts/system-overlay-services.txt
grep -q 'isForeground=true' artifacts/system-overlay-services.txt

# ci_android_journey.sh immediately before this smoke test has already validated
# the accessibility engine end-to-end, including scanning, transformation,
# restoration, safe scrolling and no-send behavior. Disable accessibility only in
# this disposable emulator now so FakeWA cannot race the persisted running/paused
# flags while the independent system overlay itself is being tested.
adb shell settings put secure enabled_accessibility_services null
adb shell settings put secure accessibility_enabled 0
sleep 1
ACCESSIBILITY_AFTER_ISOLATION="$(adb shell settings get secure accessibility_enabled | tr -d '\r')"
printf 'accessibility_enabled=%s\n' "$ACCESSIBILITY_AFTER_ISOLATION" > artifacts/system-overlay-accessibility-isolation.txt
test "$ACCESSIBILITY_AFTER_ISOLATION" = "0"

# Prove the control is visible outside DraftWA itself. Move to the launcher and
# use the deterministic fresh-install bubble coordinates.
adb shell input keyevent KEYCODE_HOME
sleep 1
adb exec-out screencap -p > artifacts/system-overlay-home.png || true

read SCREEN_W SCREEN_H DENSITY <<<"$(python3 - <<'PY'
import re, subprocess
size=subprocess.check_output(['adb','shell','wm','size'],text=True)
density=subprocess.check_output(['adb','shell','wm','density'],text=True)
m=re.search(r'(?:Override|Physical) size:\s*(\d+)x(\d+)',size)
d=re.search(r'(?:Override|Physical) density:\s*(\d+)',density)
if not m or not d:
    raise SystemExit('cannot resolve emulator display metrics')
print(m.group(1),m.group(2),d.group(1))
PY
)"
BUBBLE_X=$(( SCREEN_W - (39 * DENSITY + 80) / 160 ))
BUBBLE_Y=$(( (203 * DENSITY + 80) / 160 ))
printf 'screen=%sx%s density=%s bubble=%s,%s\n' "$SCREEN_W" "$SCREEN_H" "$DENSITY" "$BUBBLE_X" "$BUBBLE_Y" > artifacts/system-overlay-tap-coordinates.txt

# The canonical diagnostic intentionally stops the engine after proving the deep
# draft flow. Start it from the overlay while the Android launcher is foreground.
adb shell run-as com.draftwa.mobile cat shared_prefs/draftwa_mobile_v070.xml > artifacts/system-overlay-initial.xml
if ! grep -q 'name="running" value="true"' artifacts/system-overlay-initial.xml; then
  adb logcat -c
  adb shell input tap "$BUBBLE_X" "$BUBBLE_Y"
  sleep 2
  adb shell run-as com.draftwa.mobile cat shared_prefs/draftwa_mobile_v070.xml > artifacts/system-overlay-started.xml
  grep -q 'name="running" value="true"' artifacts/system-overlay-started.xml
  grep -q 'name="paused" value="false"' artifacts/system-overlay-started.xml
  adb logcat -d -v brief > artifacts/system-overlay-started-logcat.txt
  grep -Eq 'OVERLAY_START|OVERLAY_RESUME' artifacts/system-overlay-started-logcat.txt
  grep -q 'OVERLAY_WA_LAUNCH' artifacts/system-overlay-started-logcat.txt
  adb exec-out screencap -p > artifacts/system-overlay-started.png || true
fi

# Starting/resuming deliberately wakes WhatsApp. Accessibility is isolated for
# this smoke test, so the next tap deterministically proves that the overlay can
# pause the persisted engine state while WhatsApp — not DraftWA — is foreground.
sleep 1
adb logcat -c
adb shell input tap "$BUBBLE_X" "$BUBBLE_Y"
sleep 1
adb shell run-as com.draftwa.mobile cat shared_prefs/draftwa_mobile_v070.xml > artifacts/system-overlay-paused.xml
grep -q 'name="running" value="false"' artifacts/system-overlay-paused.xml
grep -q 'name="paused" value="true"' artifacts/system-overlay-paused.xml
adb logcat -d -v brief > artifacts/system-overlay-paused-logcat.txt
grep -q 'OVERLAY_PAUSE' artifacts/system-overlay-paused-logcat.txt
adb exec-out screencap -p > artifacts/system-overlay-paused.png || true

# Resume from the same system overlay over WhatsApp. This validates that the
# foreground service owns the control independently of the accessibility service.
adb logcat -c
adb shell input tap "$BUBBLE_X" "$BUBBLE_Y"
sleep 2
adb shell run-as com.draftwa.mobile cat shared_prefs/draftwa_mobile_v070.xml > artifacts/system-overlay-resumed.xml
grep -q 'name="running" value="true"' artifacts/system-overlay-resumed.xml
grep -q 'name="paused" value="false"' artifacts/system-overlay-resumed.xml
adb logcat -d -v brief > artifacts/system-overlay-resumed-logcat.txt
grep -q 'OVERLAY_RESUME' artifacts/system-overlay-resumed-logcat.txt
grep -q 'OVERLAY_WA_LAUNCH' artifacts/system-overlay-resumed-logcat.txt
adb exec-out screencap -p > artifacts/system-overlay-resumed.png || true

PID="$(adb shell pidof com.draftwa.mobile | tr -d '\r')"
test -n "$PID"
adb shell dumpsys activity services com.draftwa.mobile > artifacts/system-overlay-services-final.txt || true
grep -q 'DraftOverlayService' artifacts/system-overlay-services-final.txt
grep -q 'isForeground=true' artifacts/system-overlay-services-final.txt

printf 'API=%s\nSYSTEM_APPLICATION_OVERLAY=PASS\nOUTSIDE_DRAFTWA_START=PASS\nOVER_WHATSAPP_PAUSE=PASS\nOVER_WHATSAPP_RESUME=PASS\nFOREGROUND_SERVICE=PASS\nACCESSIBILITY_ISOLATED=PASS\n' "$API_LEVEL" > artifacts/system-overlay-result.txt
