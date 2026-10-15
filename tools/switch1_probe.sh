#!/usr/bin/env bash
# Phase 0 capture for docs/switch1-support-plan.md. Usage: tools/switch1_probe.sh <label> [adb-serial]
# Run with both Joy-Cons paired in Android Bluetooth settings. Output: tools/probe-out/<label>/
set -u
label="${1:?label, e.g. thor-a13}"
adb=(adb); [ -n "${2:-}" ] && adb=(adb -s "$2")
out="$(dirname "$0")/probe-out/$label"; mkdir -p "$out"
sh() { "${adb[@]}" shell "$@"; }

state="$("${adb[@]}" get-state 2>&1)"
if [ "$state" != "device" ]; then
  echo "No usable device (adb says: $state). Fix 'adb devices' first: it must list a serial followed by 'device'." >&2
  exit 1
fi

{
  echo "model: $(sh getprop ro.product.model)"
  echo "android: $(sh getprop ro.build.version.release) (sdk $(sh getprop ro.build.version.sdk))"
  echo "kernel: $(sh uname -r)"
  echo "date: $(date +%F)"
} | tee "$out/device.txt"

echo "== 1. event nodes"            ; sh getevent -lp > "$out/getevent-lp.txt"
echo "== 4. input service"          ; sh dumpsys input > "$out/dumpsys-input.txt"
echo "== 8. device count"           ; sh dumpsys input | grep -iE "nintendo|joy-con|057e" | tee "$out/nintendo-devices.txt"
echo "== 6. hidraw / power_supply"  ; { sh ls -l /dev/hidraw\* /dev/input/; sh ls /sys/class/power_supply /sys/class/leds; } > "$out/nodes.txt" 2>&1
echo "== kernel driver"             ; sh "zcat /proc/config.gz 2>/dev/null | grep -i nintendo; ls /sys/bus/hid/drivers 2>/dev/null" > "$out/driver.txt" 2>&1
cat "$out/driver.txt"

secs=45
cat <<EOF

== 2/3. Button capture ($secs s per Joy-Con, stops by itself)
Press: every button once (incl. SL/SR, Capture/Home, stick clicks, D-pad four ways), push the stick
to all four edges, then shake the Joy-Con.
EOF
for side in left right; do
  read -r -p "Ready to capture the $side Joy-Con? Press Enter, then start pressing buttons " _
  echo "Capturing $secs s..."
  sh timeout "$secs" getevent -lt > "$out/getevent-$side.txt"
  echo "$side: $(wc -l < "$out/getevent-$side.txt") event lines"
done

cat <<EOF

== 5. Grab test (manual)
sh into the phone: adb shell, then check you can read the node:  getevent -l /dev/input/eventN
Then note whether the Joy-Con still scrolls/clicks in the system UI while a grab is held (needs Phase 3
reader; skip for now and record 'untested').

== 7/9. Manual checks - note in $out/notes.txt:
 7. Power the Joy-Con off, press a button: does it reconnect, does the node return?
 9. With a Joy-Con 2 pair on the existing UHID pad, does PXPlay list and drive it?
Done. Captures in $out
EOF
