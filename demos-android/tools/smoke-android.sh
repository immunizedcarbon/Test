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
r=ET.parse('/tmp/demos-ui.xml').getroot()
for e in r.iter():
    if e.attrib.get('text')=='DEMOS starten':
        s=e.attrib['bounds']
        import re
        a,b,c,d=map(int,re.findall(r'\d+',s))
        print((a+c)//2,(b+d)//2)
        break
else:
    raise SystemExit("Start button was not found")
PY
)
echo "Start button tap: $X $Y"
adb shell input tap "$X" "$Y"
for i in $(seq 1 45); do
  sleep 2
  adb logcat -d -v brief > /tmp/demos-logcat.txt
  if grep -E 'DEMOS VERSION 3.0' /tmp/demos-logcat.txt; then
    echo "SUCCESS: Actual DEMOS 3.0 guest OS banner appeared in Android emulator"
    adb exec-out screencap -p > /tmp/demos-screen.png
    exit 0
  fi
done
adb exec-out screencap -p > /tmp/demos-screen.png
echo "Guest did not reach DEMOS 3.0 kernel banner"
grep -E 'DEMOS-guest|AndroidRuntime|FATAL EXCEPTION|linker|SIGSEGV|AVC' /tmp/demos-logcat.txt | tail -120 || true
exit 1
