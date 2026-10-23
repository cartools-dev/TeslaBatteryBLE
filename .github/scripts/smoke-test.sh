#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Smoke test of the shrunk demo build on a booted emulator.
#
# Usage: smoke-test.sh <apk> <output-dir> <package> <activity>
#
# Installs the APK, opens the simulated car, pairs, wakes it and waits for the
# charge line. That line needs the pairing handshake, an authenticated read and
# the battery card, so it only shows when the minified app works. Exit 0 only
# then. On failure the window texts, the focused window, a logcat tail and the
# crash buffer land in <output-dir> for the job log. The screenshot walk lives
# in capture-screenshots.sh (the daily exploratory job and local demo runs).
set -euo pipefail

APK="${1:?usage: smoke-test.sh <apk> <output-dir> <package> <activity>}"
OUT="${2:?usage: smoke-test.sh <apk> <output-dir> <package> <activity>}"
PKG="${3:?usage: smoke-test.sh <apk> <output-dir> <package> <activity>}"
ACTIVITY="${4:?usage: smoke-test.sh <apk> <output-dir> <package> <activity>}"

mkdir -p "$OUT"
DUMP="$(mktemp)"
trap 'rm -f "$DUMP"' EXIT

fail() {
  echo "::error::smoke test failed: $1" >&2
  adb exec-out screencap -p > "$OUT/failure.png" 2>/dev/null || true
  adb shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' > "$OUT/focus.txt" || true
  adb logcat -d -v time -t 1000 > "$OUT/logcat.txt" 2>/dev/null || true
  adb logcat -d -v time -b crash > "$OUT/crash.txt" 2>/dev/null || true
  grep -oE '(text|content-desc)="[^"]+"' "$DUMP" 2>/dev/null | head -n 30 > "$OUT/window-texts.txt" || true
  exit 1
}

# sys.boot_completed is not enough: the input service and package manager can
# lag minutes behind on a slow emulator.
adb wait-for-device
ready=false
for _ in $(seq 1 96); do
  if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] &&
    adb shell service check input 2>/dev/null | grep -q found &&
    adb shell pm path android 2>/dev/null | grep -q '^package:'; then
    ready=true
    break
  fi
  sleep 5
done
[ "$ready" = true ] || fail "emulator framework did not become ready"

# A stuck launcher's "isn't responding" dialog would take input focus.
adb shell settings put global hide_error_dialogs 1 || true
adb shell svc power stayon true || true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true

for attempt in 1 2 3; do
  adb install -r "$APK" && break
  [ "$attempt" -lt 3 ] || fail "could not install $APK"
  sleep 10
done
adb shell pm clear "$PKG" > /dev/null 2>&1 || true
for permission in BLUETOOTH_SCAN BLUETOOTH_CONNECT ACCESS_FINE_LOCATION POST_NOTIFICATIONS; do
  adb shell pm grant "$PKG" "android.permission.$permission" || true
done
adb shell cmd location set-location-enabled true || true

# Dump the window; a failed dump counts as "not there yet".
dump() {
  adb shell uiautomator dump /sdcard/window.xml > /dev/null 2>&1 &&
    adb pull /sdcard/window.xml "$DUMP" > /dev/null 2>&1
}

# Wait until the hierarchy matches an extended regex.
wait_for() {
  local pattern="$1" timeout="${2:-30}" waited=0
  while [ "$waited" -lt "$timeout" ]; do
    if dump && grep -qE "$pattern" "$DUMP"; then
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done
  return 1
}

# Tap the centre of the first node whose text starts with $1.
tap_text() {
  local node bounds
  wait_for "text=\"$1" 30 || return 1
  node="$(tr '>' '\n' < "$DUMP" | grep -F "text=\"$1" | head -n 1)"
  bounds="$(grep -oE '\[-?[0-9]+,-?[0-9]+\]\[-?[0-9]+,-?[0-9]+\]' <<< "$node" | head -n 1)"
  [ -n "$bounds" ] || return 1
  # "[x1,y1][x2,y2]" to the centre point
  # shellcheck disable=SC2046
  adb shell input tap $(tr '][' ',,' <<< "$bounds" | awk -F',+' '{print int(($2 + $4) / 2), int(($3 + $5) / 2)}')
}

# Material3 pull-to-refresh needs a held drag; one pull is one refresh action.
pull_until() {
  local pattern="$1" attempt
  for attempt in 1 2 3 4; do
    adb shell input swipe 540 1200 540 1800 2000
    wait_for "$pattern" 20 && return 0
  done
  return 1
}

adb shell am start -W -n "$ACTIVITY" > /dev/null
wait_for 'text="AA:BB:CC:DD:EE:01' 40 || pull_until 'text="AA:BB:CC:DD:EE:01' || fail "the simulated car was never listed"
tap_text "AA:BB:CC:DD:EE:01" || fail "could not open the simulated car"
wait_for 'text="Battery history' 30 || fail "the car screen did not open"
if wait_for 'text="Pair key' 5; then
  tap_text "Pair key" || fail "could not tap Pair key"
  wait_for 'text="App key paired' 45 || fail "pairing did not finish"
fi
pull_until 'Awake' || fail "the car never reported awake"
pull_until 'Charge limit|Charging to' || fail "no battery reading appeared"

if adb logcat -d -b crash 2>/dev/null | grep -q "$PKG"; then
  fail "the app crashed while the flow ran"
fi
echo "smoke test passed"
