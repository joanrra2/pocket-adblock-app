#!/usr/bin/env bash
# End-to-end test on a running Android emulator (or phone):
#   fake board -> Pocket AdBlock (find, log in, save) -> Set up Pocket Wake -> Pocket Wake
#   opens with the board's broker filled in.
# Usage: tools/e2e_android.sh <pocket-adblock-debug.apk> <pocket-wake-debug.apk> [out-dir]
# Uses only test values: the fake board's test login and a made-up broker.
set -euo pipefail
APP_APK=${1:?Pocket AdBlock debug APK}
WAKE_APK=${2:?Pocket Wake debug APK}
OUT=${3:-e2e-out}
mkdir -p "$OUT"
PKG=io.github.joanrra2.pocketadblock.debug
WAKE=io.github.joanrra2.pocketwake.debug

shot() { adb exec-out screencap -p > "$OUT/$1.png" || true; }
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true; adb shell cat /sdcard/ui.xml 2>/dev/null | tr '>' '\n'; }
fail() { echo "FAIL: $*"; shot "failure"; dump > "$OUT/failure.xml"; exit 1; }
step() { echo "== $*"; }

# Waits up to 30 s for a UI element matching the grep pattern.
wait_for() {
  for _ in $(seq 1 30); do dump | grep -q -- "$1" && return 0; sleep 1; done
  fail "not on screen: $1"
}

# Taps the last element matching the grep pattern (waits for it first).
tap() {
  wait_for "$1"
  local b
  b=$(dump | grep -- "$1" | tail -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

step "fake board on 127.0.0.1:8080, reachable from the emulator"
python3 -u tools/fake_board.py 8080 > "$OUT/fake_board.log" 2>&1 &
FAKE_PID=$!
trap 'kill $FAKE_PID 2>/dev/null || true' EXIT
sleep 1
adb reverse tcp:8080 tcp:8080

step "install both apps"
adb install -r "$APP_APK"
adb install -r "$WAKE_APK"

step "Pocket AdBlock: type the address, log in, save"
adb shell am start -n "$PKG/io.github.joanrra2.pocketadblock.MainActivity"
tap 'resource-id="address-field"'
adb shell input text "127.0.0.1:8080"
tap 'resource-id="address-try"'
tap 'text="Password"'
adb shell input text "test-password-123"
# Enter = the password field's Go action (the keyboard would cover the Connect button).
adb shell input keyevent KEYCODE_ENTER
wait_for 'text="fake-board"'
shot "1-board-page"

step "back to the board list"
adb shell input keyevent KEYCODE_BACK
wait_for 'resource-id="menu-fake-board"'
shot "2-board-list"

step "Set up Pocket Wake"
tap 'resource-id="menu-fake-board"'
tap 'text="Set up Pocket Wake"'
wait_for 'text="broker.example.test · 8883 · TLS"'
shot "3-pocket-wake-dialog"
tap 'text="Open in Pocket Wake"'

step "Pocket Wake opens with the board's broker filled in"
for _ in $(seq 1 20); do
  adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | grep -q "$WAKE" && break
  sleep 1
done
adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | grep -q "$WAKE" || fail "Pocket Wake is not in front"
wait_for 'Filled in from the board'
wait_for 'text="broker.example.test"'
wait_for 'text="8883"'
shot "4-pocket-wake"
# The topic prefix is further down the form.
read -r W H < <(adb shell wm size | grep -o '[0-9]\+x[0-9]\+' | tail -1 | tr 'x' ' ')
adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 4)) 300
wait_for 'text="sinkhole"'
shot "5-pocket-wake-prefix"
grep -q "200 /api/settings" "$OUT/fake_board.log" || fail "the app never read /api/settings"

echo "PASS: board found, logged in, saved, and its broker handed to Pocket Wake"
