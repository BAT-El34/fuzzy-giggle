#!/usr/bin/env bash
set -euo pipefail

API_LEVEL="${1:?API level required}"
mkdir -p artifacts
DRAFTWA="app/build/outputs/apk/debug/app-debug.apk"
FAKEWA="fakewa/build/outputs/apk/debug/fakewa-debug.apk"
SERVICE="com.draftwa.mobile/com.draftwa.mobile.DraftAccessibilityService"

adb install -r "$FAKEWA"
adb install -r "$DRAFTWA"

adb shell settings put secure enabled_accessibility_services "$SERVICE"
adb shell settings put secure accessibility_enabled 1
ENABLED_SERVICES="$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')"
ACCESSIBILITY_ENABLED="$(adb shell settings get secure accessibility_enabled | tr -d '\r')"
printf 'enabled_accessibility_services=%s\naccessibility_enabled=%s\n' "$ENABLED_SERVICES" "$ACCESSIBILITY_ENABLED" > artifacts/accessibility-settings.txt
[[ "$ENABLED_SERVICES" == *"com.draftwa.mobile"* ]]
[[ "$ACCESSIBILITY_ENABLED" == "1" ]]

adb shell am force-stop com.draftwa.mobile
adb logcat -c
adb shell monkey -p com.draftwa.mobile -c android.intent.category.LAUNCHER 1
sleep 4

adb exec-out screencap -p > artifacts/draftwa-home.png || true
adb shell uiautomator dump /sdcard/draftwa-home.xml >/dev/null
adb pull /sdcard/draftwa-home.xml artifacts/draftwa-home.xml >/dev/null

FOUND=0
for ATTEMPT in 1 2 3 4 5 6 7 8; do
  adb shell uiautomator dump /sdcard/draftwa.xml >/dev/null
  adb pull /sdcard/draftwa.xml artifacts/draftwa.xml >/dev/null
  if python3 - <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse('artifacts/draftwa.xml').getroot()
labels={'Tester en diagnostic','Reprendre','Démarrer le moteur','Démarrer'}
for n in root.iter('node'):
    if n.attrib.get('text') in labels and n.attrib.get('enabled','true') == 'true':
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
        if not m:
            continue
        x1,y1,x2,y2=map(int,m.groups())
        # Refuse a button that is merely clipped into the bottom navigation area.
        visible_h=max(0, min(y2, 2320)-max(y1, 80))
        if visible_h < 60 or y1 >= 2300:
            continue
        x=(x1+x2)//2
        y=min((y1+y2)//2, 2280)
        subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
        raise SystemExit(0)
raise SystemExit(9)
PY
  then
    FOUND=1
    break
  fi
  adb shell input swipe 540 1800 540 900 300
  sleep 1
done

test "$FOUND" = "1"
sleep 2
adb shell uiautomator dump /sdcard/after-start.xml >/dev/null || true
adb pull /sdcard/after-start.xml artifacts/after-start.xml >/dev/null || true
sleep 28

adb exec-out screencap -p > artifacts/final.png || true
adb shell uiautomator dump /sdcard/final.xml >/dev/null || true
adb pull /sdcard/final.xml artifacts/final.xml >/dev/null || true
adb logcat -d -v threadtime > artifacts/logcat.txt
adb shell run-as com.whatsapp.w4b cat shared_prefs/ci.xml > artifacts/fakewa-ci.xml 2>/dev/null || true
cat artifacts/fakewa-ci.xml || true

grep -q 'ACCESSIBILITY_CONNECTED' artifacts/logcat.txt
grep -q 'RUN_START' artifacts/logcat.txt
grep -q 'name="opened" value="true"' artifacts/fakewa-ci.xml
grep -q 'name="transformed_seen" value="true"' artifacts/fakewa-ci.xml
grep -q 'name="restored" value="true"' artifacts/fakewa-ci.xml
! grep -q 'name="sent" value="true"' artifacts/fakewa-ci.xml

grep -q 'DRAFT_SCAN_PAGE' artifacts/logcat.txt
grep -q 'DRAFT_INDICATORS' artifacts/logcat.txt
grep -q 'DRAFT_OPENED' artifacts/logcat.txt
grep -q 'EDITOR_FOUND' artifacts/logcat.txt
grep -q 'CONFIRMATION_PASS' artifacts/logcat.txt
grep -q 'TRANSFORM_PASS' artifacts/logcat.txt
grep -q 'RESTORED_OK' artifacts/logcat.txt
grep -q 'SEND_SKIPPED_DIAGNOSTIC' artifacts/logcat.txt

PID="$(adb shell pidof com.draftwa.mobile | tr -d '\r')"
test -n "$PID"
if grep -A30 'FATAL EXCEPTION' artifacts/logcat.txt | grep -q 'com.draftwa.mobile'; then
  echo 'DraftWA crashed'
  exit 1
fi

printf 'API=%s\nDIAGNOSTIC_FLOW=PASS\nACCESSIBILITY=PASS\nNO_SEND=PASS\nRESTORE=PASS\nNO_FATAL=PASS\n' "$API_LEVEL" > artifacts/result.txt
