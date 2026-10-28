#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
DEVICE="${1:-emulator-5554}"
mkdir -p "$ROOT/runtime"
"$ADB" -s "$DEVICE" install -r "$ROOT/android-demo/app/build/outputs/apk/debug/app-debug.apk"
# Read only logs from this newly started process, avoiding stale success records.
"$ADB" -s "$DEVICE" shell am force-stop top.cyclops.printer
"$ADB" -s "$DEVICE" shell am start -n top.cyclops.printer/.MainActivity --ez auto_print true
APP_PID=""
for attempt in {1..20}; do
  APP_PID="$("$ADB" -s "$DEVICE" shell pidof top.cyclops.printer | tr -d '\r' || true)"
  [ -n "$APP_PID" ] && break
  sleep 0.2
done
[ -n "$APP_PID" ] || { echo 'App did not start' >&2; exit 1; }
for attempt in {1..45}; do
  "$ADB" -s "$DEVICE" logcat -d --pid="$APP_PID" -s PrinterDemo:I '*:S' > "$ROOT/runtime/android-print.log"
  if grep -q DEMO_SUCCESS "$ROOT/runtime/android-print.log"; then
    cat "$ROOT/runtime/android-print.log"
    "$ADB" -s "$DEVICE" exec-out run-as top.cyclops.printer cat files/demo.pdf > "$ROOT/runtime/android-original.pdf"
    echo "Android IPP job completed. Compare runtime/android-original.pdf with runtime/spool/*.pdf."
    exit 0
  fi
  sleep 1
done
cat "$ROOT/runtime/android-print.log"
echo 'No completed job observed. Inspect app and runtime/ippserver.log.' >&2
exit 1
