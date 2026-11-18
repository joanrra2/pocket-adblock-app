#!/usr/bin/env bash
# End-to-end test on a running Android emulator (or phone):
#   fake board -> Pocket AdBlock: wrong passwords hit the login limit (429), log in, save,
#   install the offered update and restart -> Set up Pocket Wake -> Pocket Wake opens
#   with the board's broker filled in.
# Usage: tools/e2e_android.sh <pocket-adblock.apk> <pocket-wake.apk> [out-dir]
# Debug APKs by default; for release APKs set E2E_APP_PKG=io.github.joanrra2.pocketadblock
# and E2E_WAKE_PKG=io.github.joanrra2.pocketwake.
# Uses only test values: the fake board's test login and a made-up broker.
set -euo pipefail
APP_APK=${1:?Pocket AdBlock APK}
WAKE_APK=${2:?Pocket Wake APK}
OUT=${3:-e2e-out}
mkdir -p "$OUT"
PKG=${E2E_APP_PKG:-io.github.joanrra2.pocketadblock.debug}
WAKE=${E2E_WAKE_PKG:-io.github.joanrra2.pocketwake.debug}

shot() { adb exec-out screencap -p > "$OUT/$1.png" || true; }
# Never fails (set -e + pipefail would end the test silently when one dump misses).
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true; { adb shell cat /sdcard/ui.xml 2>/dev/null || true; } | tr '>' '\n' || true; }
fail() { echo "FAIL: $*"; shot "failure"; dump > "$OUT/failure.xml"; exit 1; }
step() { echo "== $*"; }

# Slow CI emulators sometimes show "<system app> isn't responding" (usually the Pixel
# Launcher) over everything. Close that app; the apps under test run on their own.
dismiss_anr() {
  local d b
  d=$(dump)
  grep -q "isn't responding" <<<"$d" || return 0
  echo "closing a system 'isn't responding' dialog"
  b=$(grep 'text="Close app"' <<<"$d" | tail -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ' || true)
  [ -n "$b" ] || return 0
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
  sleep 1
}

# Waits up to 30 s for a UI element matching the grep pattern.
wait_for() {
  for _ in $(seq 1 30); do
    dismiss_anr
    dump | grep -q -- "$1" && return 0
    sleep 1
  done
  fail "not on screen: $1"
}

# Waits up to 90 s for a UI element matching the grep pattern to go away.
wait_gone() {
  for _ in $(seq 1 90); do
    dump | grep -q -- "$1" || return 0
    sleep 1
  done
  fail "still on screen: $1"
}

# Taps the last element matching the grep pattern, retrying for up to 30 s (a dump can
# miss an element for a moment while the screen recomposes).
tap() {
  local b=""
  for _ in $(seq 1 30); do
    dismiss_anr
    b=$(dump | grep -- "$1" | tail -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ' || true)
    [ -n "$b" ] && break
    sleep 1
  done
  [ -n "$b" ] || fail "not on screen: $1"
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

# Empties the focused text field.
clear_field() {
  adb shell input keyevent KEYCODE_MOVE_END
  for _ in $(seq 1 30); do adb shell input keyevent KEYCODE_DEL; done
}

# Types into the focused password field and checks the number of dots: emulator input
# sometimes drops or repeats a character. Clears and retries up to 3 times.
type_password() {
  local want=${#1} dots
  for _ in 1 2 3; do
    sleep 1
    adb shell input text "$1"
    sleep 1
    dots=$(dump | grep -o 'text="•\+"' | head -1 | tr -cd '•' | wc -m || true)
    dots=${dots:-0}
    [ "$dots" -eq "$want" ] && return 0
    echo "typed $dots of $want characters, retrying"
    clear_field
  done
  fail "could not type the password"
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
sleep 1
adb shell input text "127.0.0.1:8080"
wait_for 'text="127.0.0.1:8080"'
tap 'resource-id="address-try"'
tap 'text="Password"'
# Enter = the password field's Go action (the keyboard would cover the Connect button).
step "login limit: a refused password is not sent twice; 3 different ones make the board wait"
type_password "wrong-one"
adb shell input keyevent KEYCODE_ENTER
wait_for 'text="Wrong user or password."'
adb shell input keyevent KEYCODE_ENTER   # same password again: the app must not send it
sleep 2
for pw in wrong-two wrong-three wrong-four; do
  clear_field
  type_password "$pw"
  adb shell input keyevent KEYCODE_ENTER
  sleep 2
done
wait_for 'Too many wrong passwords'
shot "0-login-limit"
wrong=$(grep -c "auth wrong" "$OUT/fake_board.log" || true)
limited=$(grep -c "^429 " "$OUT/fake_board.log" || true)
echo "wrong passwords sent: $wrong, refused with 429: $limited"
[ "$wrong" -eq 3 ] || fail "expected 3 wrong passwords sent (one repeat skipped), got $wrong"
[ "$limited" -eq 1 ] || fail "expected one 429, got $limited"
wait_gone 'Too many wrong passwords'   # the fake board's first wait is 30 s; Connect comes back by itself
clear_field
type_password "test-password-123"
adb shell input keyevent KEYCODE_ENTER
wait_for 'text="fake-board"'
shot "1-board-page"

step "back to the board list"
adb shell input keyevent KEYCODE_BACK
wait_for 'resource-id="menu-fake-board"'
wait_for 'text="update 0.5.2"'
shot "2-board-list"

step "install the offered update, then restart"
tap 'resource-id="menu-fake-board"'
tap 'text="Install update 0.5.2"'
tap 'resource-id="install-confirm"'
wait_for 'is installing the update'
wait_for 'text="installing update"'
grep -q "200 POST /api/firmware/install" "$OUT/fake_board.log" || fail "install was not sent"
tap 'resource-id="menu-fake-board"'
tap 'resource-id="menu-restart"'
tap 'resource-id="restart-confirm"'
wait_for 'is restarting'
wait_for 'v0.5.2'
shot "2b-updated"

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
