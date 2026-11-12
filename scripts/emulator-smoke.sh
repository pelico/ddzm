#!/usr/bin/env bash
# Cloud-side smoke test. Runs inside reactivecircus/android-emulator-runner.
# Verifies: APK installs, launcher activity starts without crashing, and
# (best effort) the HOME role can be granted via shell.
set -euo pipefail

PKG="app.olauncher.debug"
ACTIVITY="app.olauncher.MainActivity"
OUT="smoke-artifacts"
mkdir -p "$OUT"

echo "== build debug APK =="
./gradlew --stacktrace -Dorg.gradle.jvmargs=-Xmx3072m assembleDebug
APK=$(find app/build/outputs/apk/debug -name '*.apk' -print -quit)
test -n "$APK" || { echo "::error::no APK produced"; exit 1; }
echo "APK=$APK ($(stat -c%s "$APK") bytes)"

echo "== install =="
adb install -r -t "$APK"

echo "== clear logcat and launch launcher activity =="
adb logcat -c || true
adb shell am start -n "$PKG/$ACTIVITY"
sleep 5

echo "== process / focus / crash check =="
adb shell pidof "$PKG" | tee "$OUT/pid.txt" || true
adb shell dumpsys activity activities 2>/dev/null \
  | grep -iE 'ResumedActivity|topResumed|mResumed' | head -10 \
  | tee "$OUT/activities.txt" || true
adb logcat -d -t 500 > "$OUT/logcat.txt" || true
adb exec-out screencap -p > "$OUT/launcher.png" || true

if grep -q 'FATAL EXCEPTION' "$OUT/logcat.txt"; then
  echo "::error::crash detected in logcat"
  grep -A20 'FATAL EXCEPTION' "$OUT/logcat.txt" | head -40
  exit 1
fi
if [ ! -s "$OUT/pid.txt" ]; then
  echo "::error::launcher process is not running after start"
  exit 1
fi

echo "== best-effort HOME role (shell identity) =="
ROLE_OK=0
if adb shell cmd role add-role-holder android.app.role.HOME "$PKG"; then
  ROLE_OK=1
elif adb shell cmd package set-home-activity "$PKG/$ACTIVITY"; then
  ROLE_OK=1
else
  echo "::warning::this emulator image refused to grant HOME via shell"
fi

if [ "$ROLE_OK" = "1" ]; then
  adb shell cmd role get-role-holders android.app.role.HOME | tee "$OUT/home-holders.txt" || true
  adb shell input keyevent KEYCODE_HOME
  sleep 2
  adb shell dumpsys window 2>/dev/null | grep -iE 'mCurrentFocus|mFocusedApp' | head -5 \
    | tee "$OUT/focus-after-home.txt" || true
  adb exec-out screencap -p > "$OUT/after-home.png" || true
fi

echo "SMOKE OK (role_granted=$ROLE_OK)"
