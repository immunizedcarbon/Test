#!/usr/bin/env bash
set -euo pipefail
APK=installed/demos-android/app/build/outputs/apk/debug/app-debug.apk
test -s "$APK"
adb devices -l
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abi
adb install -r "$APK"
adb shell am start -n org.demos.dvk/.MainActivity
sleep 3
adb shell uiautomator dump /sdcard/demos-ui.xml
adb exec-out cat /sdcard/demos-ui.xml > /tmp/demos-ui.xml
read -r X Y < <(python3 - <<'PY'
import xml.etree.ElementTree as ET
import re
root=ET.parse('/tmp/demos-ui.xml').getroot()
for e in root.iter():
    if e.attrib.get('text')=='DEMOS starten':
        a,b,c,d=map(int,re.findall(r'\d+',e.attrib['bounds']))
        print((a+c)//2,(b+d)//2)
        break
else:
    raise SystemExit('Start button not found')
PY
)
echo "Tap DEMOS starten: $X $Y"
adb shell input tap "$X" "$Y"
for i in $(seq 1 65); do
  sleep 2
  adb logcat -d -v brief -s 'DEMOS-guest:I' '*:S' > /tmp/demos-logcat.txt
  if python3 - /tmp/demos-logcat.txt <<'PY'
import sys
data=open(sys.argv[1],encoding='utf8',errors='replace').read().lower()
kernel='demos version 3.0' in data
login=any(marker in data for marker in ['(dwk-4)', 'wersiq 3.0', '1991, mp'])
if kernel and login:
    print('SUCCESS: DEMOS 3.0 kernel AND historical login screen reached in Android')
    sys.exit(0)
sys.exit(1)
PY
  then
    adb exec-out screencap -p > /tmp/demos-screen.png
    adb shell uiautomator dump /sdcard/demos-ui-final.xml || true
    adb exec-out cat /sdcard/demos-ui-final.xml > /tmp/demos-ui-final.xml || true
    exit 0
  fi
done
adb exec-out screencap -p > /tmp/demos-screen.png
echo "FAIL: Guest login banner not reached"
tail -200 /tmp/demos-logcat.txt || true
exit 1
