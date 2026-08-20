#!/usr/bin/env bash
set -euo pipefail

API_LEVEL="${1:?API level required}"
mkdir -p artifacts
DRAFTWA="app/build/outputs/apk/debug/app-debug.apk"
FAKEWA="fakewa/build/outputs/apk/debug/fakewa-debug.apk"

adb install -r "$FAKEWA"
adb install -r "$DRAFTWA"

adb shell settings put secure enabled_accessibility_services com.draftwa.mobile/com.draftwa.mobile.DraftAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell am force-stop com.draftwa.mobile
adb logcat -c
adb shell monkey -p com.draftwa.mobile -c android.intent.category.LAUNCHER 1
sleep 3

adb exec-out screencap -p > artifacts/draftwa-home.png || true
adb shell uiautomator dump /sdcard/draftwa-home.xml >/dev/null
adb pull /sdcard/draftwa-home.xml artifacts/draftwa-home.xml >/dev/null

FOUND=0
for ATTEMPT in 1 2 3 4 5 6; do
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
        x=(int(m.group(1))+int(m.group(3)))//2
        y=(int(m.group(2))+int(m.group(4)))//2
        subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
        raise SystemExit(0)
raise SystemExit(9)
PY
  then
    FOUND=1
    break
  fi
  adb shell input swipe 540 1750 540 650 300
  sleep 1
done

test "$FOUND" = "1"
sleep 24

adb exec-out screencap -p > artifacts/final.png || true
adb shell uiautomator dump /sdcard/final.xml >/dev/null || true
adb pull /sdcard/final.xml artifacts/final.xml >/dev/null || true
adb logcat -d -v threadtime > artifacts/logcat.txt
adb shell run-as com.whatsapp.w4b cat shared_prefs/ci.xml > artifacts/fakewa-ci.xml 2>/dev/null || true
cat artifacts/fakewa-ci.xml || true

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

printf 'API=%s\nDIAGNOSTIC_FLOW=PASS\nNO_SEND=PASS\nRESTORE=PASS\nNO_FATAL=PASS\n' "$API_LEVEL" > artifacts/result.txt
