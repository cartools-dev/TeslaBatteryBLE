#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Exploratory pass on a booted emulator: crashes, ANRs and broken values.
#
# Usage: explore.sh <apk> <output-dir> [package] [activity]
#
# Runs the scripted screenshot walk first (capture-screenshots.sh, the same flow
# as the release smoke test), then stresses the car screen (font scale,
# rotation, process death) and finishes with a seeded monkey run. Everything is
# judged from text: the uiautomator tree and logcat. Screenshots from the walk
# stay in the output directory for a failed run and are not read otherwise.
#
# Findings go to <output-dir>/findings/*.md, one file per finding; the first
# line is the issue title. The script exits 0 when it ran to the end, whether or
# not it found anything, and non-zero only when the emulator could not be used.
#
# Environment: EXPLORE_SEED (monkey seed), EXPLORE_EVENTS (monkey events),
# EXPLORE_THROTTLE_MS (delay between monkey events).
set -euo pipefail

export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

APK="${1:?usage: explore.sh <apk> <output-dir> [package] [activity]}"
OUT="${2:?usage: explore.sh <apk> <output-dir> [package] [activity]}"
PKG="${3:-com.dzid26.teslable}"
ACTIVITY="${4:-$PKG/.MainActivity}"
SEED="${EXPLORE_SEED:-$(date -u +%Y%m%d)}"
EVENTS="${EXPLORE_EVENTS:-5000}"
THROTTLE="${EXPLORE_THROTTLE_MS:-100}"

SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FINDINGS="$OUT/findings"
mkdir -p "$FINDINGS"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

finding_count=0

# add_finding <kind> <signature> <detail-file-or-text>
# The title is stable for the same kind and signature, so the reporting job can
# fold repeats into one issue.
add_finding() {
  local kind="$1" signature="$2" detail="$3"
  finding_count=$((finding_count + 1))
  local file="$FINDINGS/$finding_count.md"
  {
    echo "Exploratory: $kind $signature"
    echo
    echo "Monkey seed: \`$SEED\` ($EVENTS events, throttle ${THROTTLE}ms). Package: \`$PKG\`."
    echo
    echo '```'
    if [ -f "$detail" ]; then head -n 60 "$detail"; else printf '%s\n' "$detail"; fi
    echo '```'
  } > "$file"
  echo "FINDING: $kind $signature" >&2
}

# --------------------------------------------------------------- scripted walk
# The walk installs the APK, grants permissions, pairs with the simulated car and
# visits the list, the car and Settings in light and dark.
if ! bash "$SCRIPTS/capture-screenshots.sh" "$APK" "$OUT/walk" "$PKG" "$ACTIVITY" \
  > "$WORK/walk.log" 2>&1; then
  tail -n 40 "$WORK/walk.log" > "$WORK/walk-tail.log"
  add_finding "scripted walk failed" "(see walk log)" "$WORK/walk-tail.log"
fi
cat "$WORK/walk.log"

# ------------------------------------------------------------------ UI helpers
DUMP="$WORK/window.xml"

ui_dump() {
  adb shell uiautomator dump /sdcard/window.xml > /dev/null 2>&1 &&
    adb pull /sdcard/window.xml "$DUMP" > /dev/null 2>&1
}

# Texts that mean a value was never formatted. Matched whole-word on the text
# and content-desc attributes only, so resource ids and class names cannot hit.
check_dump() {
  local label="$1" hits
  ui_dump || { echo "  ... no dump at $label" >&2; return 0; }
  hits="$(grep -oE '(text|content-desc)="[^"]*"' "$DUMP" |
    grep -E '(^|[^A-Za-z])(null|NaN|Infinity|undefined)([^A-Za-z]|$)' | sort -u | head -n 10 || true)"
  if [ -n "$hits" ]; then
    add_finding "unformatted value" "at $label" "$hits"
  fi
}

# Count the crash/ANR entries in logcat and add one finding per distinct kind.
check_logcat() {
  local label="$1" crash anr
  adb logcat -d -v threadtime -b crash > "$WORK/crash.log" 2>/dev/null || true
  if grep -q 'FATAL EXCEPTION' "$WORK/crash.log"; then
    crash="$(sed -E 's/^.* E AndroidRuntime: //' "$WORK/crash.log" |
      grep -m1 -E '^[A-Za-z0-9_.$]+(Exception|Error)' | cut -d: -f1 || true)"
    add_finding "crash" "${crash:-unknown exception} at $label" "$WORK/crash.log"
  fi
  adb logcat -d -v threadtime -b main -b system > "$WORK/main.log" 2>/dev/null || true
  anr="$(grep -m1 "ANR in $PKG" "$WORK/main.log" || true)"
  if [ -n "$anr" ]; then
    grep -A12 "ANR in $PKG" "$WORK/main.log" > "$WORK/anr.log" || true
    add_finding "ANR" "at $label" "$WORK/anr.log"
  fi
}

launch() {
  adb shell am start -W -n "$ACTIVITY" > /dev/null 2>&1 || true
  sleep 3
}

# Open the simulated car from the list when the list is showing.
open_car() {
  local node bounds
  ui_dump || return 0
  node="$(tr '>' '\n' < "$DUMP" | grep -F 'text="Demo Tesla' | head -n 1 || true)"
  bounds="$(grep -oE 'bounds="\[-?[0-9]+,-?[0-9]+\]\[-?[0-9]+,-?[0-9]+\]"' <<< "$node" | head -n 1 || true)"
  [ -n "$bounds" ] || return 0
  # shellcheck disable=SC2046
  adb shell input tap $(sed -E 's/.*\[(-?[0-9]+),(-?[0-9]+)\]\[(-?[0-9]+),(-?[0-9]+)\].*/\1 \3 \2 \4/' <<< "$bounds" |
    awk '{print int(($1 + $2) / 2), int(($3 + $4) / 2)}')
  sleep 3
}

# ------------------------------------------------------------------- stressors
adb logcat -c || true
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS > /dev/null 2>&1 || true
adb shell cmd uimode night no > /dev/null 2>&1 || true
launch
open_car
check_dump "car screen"

# Large text.
adb shell settings put system font_scale 1.3 || true
sleep 3
launch
check_dump "font scale 1.3"
adb shell settings put system font_scale 1.0 || true

# Landscape, then back.
adb shell settings put system accelerometer_rotation 0 || true
adb shell settings put system user_rotation 1 || true
sleep 3
check_dump "landscape"
adb shell settings put system user_rotation 0 || true
sleep 2

# Process death with a saved task: the app must come back without a crash.
adb shell input keyevent KEYCODE_HOME || true
sleep 1
adb shell am kill "$PKG" || true
sleep 2
launch
check_dump "after process death"
check_logcat "scripted stress"

# ---------------------------------------------------------------------- monkey
adb logcat -c || true
adb shell monkey -p "$PKG" -s "$SEED" --throttle "$THROTTLE" \
  --pct-syskeys 0 --pct-appswitch 0 -v "$EVENTS" > "$WORK/monkey.log" 2>&1 || true
tail -n 5 "$WORK/monkey.log"
if grep -q -E 'CRASH:|ANR in' "$WORK/monkey.log"; then
  echo "  monkey stopped early, see findings" >&2
fi
check_logcat "monkey"
check_dump "after monkey"

echo "exploration finished: $finding_count finding(s)"
