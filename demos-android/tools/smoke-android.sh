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
for i in $(seq 1 60); do
  sleep 2
  adb logcat -d -v brief > /tmp/demos-logcat.txt
  if python3 - /tmp/demos-logcat.txt <<'PY'
import re,sys
log=open(sys.argv[1],encoding='utf8',errors='replace').read()
chunks=re.findall(r'^I/DEMOS-guest\(.*?\): ?(.*)
grep -E 'DEMOS-guest|AndroidRuntime|FATAL EXCEPTION|linker|SIGSEGV|AVC' /tmp/demos-logcat.txt | tail -120 || true
exit 1
,log,re.M)
output=''.join(chunks).lower()
kernel='demos version 3.0' in output
login='(dwk-4)' in output or '1991, mp' in output or 'wersiq 3.0' in output
if login and kernel:
    print('SUCCESS: Android guest reached authentic DEMOS kernel and historical login banner')
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
echo "Guest did not reach full historical DEMOS login banner"
python3 - /tmp/demos-logcat.txt <<'PY'
import re,sys
data=open(sys.argv[1],encoding='utf8',errors='replace').read()
chunks=re.findall(r'^I/DEMOS-guest\(.*?\): ?(.*)
grep -E 'DEMOS-guest|AndroidRuntime|FATAL EXCEPTION|linker|SIGSEGV|AVC' /tmp/demos-logcat.txt | tail -120 || true
exit 1
,data,re.M)
output=''.join(chunks)
print('TAIL GUEST OUTPUT:',repr(output[-1600:]))
PY
grep -E 'DEMOS-guest|AndroidRuntime|FATAL EXCEPTION|linker|SIGSEGV|AVC' /tmp/demos-logcat.txt | tail -120 || true
exit 1
