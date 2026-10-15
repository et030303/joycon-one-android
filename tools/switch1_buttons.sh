#!/usr/bin/env bash
# Prompted per-button capture for docs/switch1.md. Usage: tools/switch1_buttons.sh <label> [adb-serial]
# For each prompt, press Enter, then press exactly that one control within 3 seconds.
# Output: tools/probe-out/<label>/buttons.tsv
set -u
export MSYS_NO_PATHCONV=1
label="${1:?label, e.g. tb305fu-a15}"
adb=(adb); [ -n "${2:-}" ] && adb=(adb -s "$2")
out="$(dirname "$0")/probe-out/$label"; mkdir -p "$out"
secs=3

[ "$("${adb[@]}" get-state 2>&1)" = "device" ] || { echo "No usable adb device." >&2; exit 1; }

node_for() {
  "${adb[@]}" shell getevent -lp | tr -d '\r' | awk -v want="$1" '
    /^add device/ { path=$4 }
    /name:/ { gsub(/.*name: *"|"$/, ""); if ($0 == want) { print path; exit } }'
}

capture() {
  local node="$1"
  "${adb[@]}" shell timeout "$secs" getevent -l "$node" | tr -d '\r'
}

prompt_button() {
  local side="$1" node="$2" name="$3" kind="$4"
  read -r -p "[$side] $name — press Enter, then do it: " _
  local raw; raw="$(capture "$node")"
  local result
  if [ "$kind" = stick ]; then
    result="$(echo "$raw" | awk '$1=="EV_ABS" && $2 ~ /^ABS_/ {
      v=strtonum("0x"$3); if (v>2147483647) v-=4294967296
      a=(v<0?-v:v); if (a>best[$2]) {best[$2]=a; val[$2]=v} }
      END { for (k in val) printf "%s=%d ", k, val[k] }')"
  else
    result="$(echo "$raw" | awk '$1=="EV_KEY" && $3=="DOWN" {print $2}' | sort -u | tr '\n' ' ')"
  fi
  [ -n "$result" ] || result="(nothing)"
  echo "   -> $result"
  printf '%s\t%s\t%s\n' "$side" "$name" "$result" >> "$out/buttons.tsv"
}

left="$(node_for "Nintendo Switch Left Joy-Con")"
right="$(node_for "Nintendo Switch Right Joy-Con")"
[ -n "$left" ] && [ -n "$right" ] || { echo "Joy-Con nodes not found (left='$left' right='$right'). Connect both." >&2; exit 1; }
echo "left=$left right=$right"
: > "$out/buttons.tsv"

for b in ZL L SL SR Minus Capture "Stick click" "D-pad up" "D-pad right"; do
  prompt_button left "$left" "$b" key
done
prompt_button left "$left" "Push stick fully UP and hold" stick
prompt_button left "$left" "Push stick fully RIGHT and hold" stick

for b in ZR R SL SR Plus Home A B X Y "Stick click"; do
  prompt_button right "$right" "$b" key
done
prompt_button right "$right" "Push stick fully UP and hold" stick
prompt_button right "$right" "Push stick fully RIGHT and hold" stick

echo; echo "Saved $out/buttons.tsv"; cat "$out/buttons.tsv"
