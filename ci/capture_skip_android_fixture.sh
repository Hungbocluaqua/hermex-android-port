#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/ci/visual-goldens/hermex-screens.json"
SCREEN="${HERMEX_VISUAL_FIXTURE_NAME:-session-list}"
STATE="${HERMEX_VISUAL_STATE:-dark}"
DEVICE_NAME="${HERMEX_VISUAL_DEVICE:-compact-phone}"
OUTPUT_ROOT="${HERMEX_VISUAL_OUTPUT_ROOT:-"$ROOT/dist/android-visual-screens"}"
DEBUG_ROOT="${HERMEX_VISUAL_DEBUG_ROOT:-"${RUNNER_TEMP:-"$ROOT/.build"}/android-visual-debug"}"
APK_DIR="${HERMEX_VISUAL_APK_DIR:-"${RUNNER_TEMP:-"$ROOT/.build"}/hermex-skip-visual-apk"}"
PACKAGE_ID="${HERMEX_SKIP_APP_ID:-com.uzairansar.hermex}"
ADB="${ADB:-adb}"
SETTLE_SECONDS="${HERMEX_VISUAL_SETTLE_SECONDS:-5}"
CAPTURE_ATTEMPTS="${HERMEX_VISUAL_CAPTURE_ATTEMPTS:-6}"
CAPTURE_RETRY_SECONDS="${HERMEX_VISUAL_CAPTURE_RETRY_SECONDS:-3}"
REUSE_APK=0
SKIP_INSTALL=0
WIDTH=""
HEIGHT=""
SELF_TEST=0
PYTHON_BIN="${PYTHON_BIN:-}"
RUNTIME_FIXTURE_FILE="hermex_visual_fixture.txt"

log() {
  printf '[%s] %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*" >&2
}

usage() {
  cat <<'USAGE'
Usage: capture_skip_android_fixture.sh [options]

Build or reuse a Skip Android APK, select one Hermex visual fixture, install it
on the currently connected emulator, and capture:
  <output-root>/<device-name>/<state>/<screen>.png

Options:
  --screen NAME        Fixture screen from ci/visual-goldens/hermex-screens.json
  --state light|dark   Android system UI mode to capture
  --device-name NAME   Device key from ci/visual-goldens/hermex-screens.json
  --output-root DIR    Screenshot artifact root
  --apk-dir DIR        Temporary Skip APK build output directory
  --package-id ID      Android package id
  --reuse-apk          Install an existing APK from --apk-dir instead of rebuilding
  --skip-install       Reuse the already installed app and only clear fixture state
  --width PX           Override emulator screenshot width
  --height PX          Override emulator screenshot height
  --settle-seconds N   Seconds to wait after launch before screenshot
  --self-test          Validate manifest/script wiring without using adb
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --screen)
      SCREEN="$2"
      shift 2
      ;;
    --state)
      STATE="$2"
      shift 2
      ;;
    --device-name)
      DEVICE_NAME="$2"
      shift 2
      ;;
    --output-root)
      OUTPUT_ROOT="$2"
      shift 2
      ;;
    --apk-dir)
      APK_DIR="$2"
      shift 2
      ;;
    --package-id)
      PACKAGE_ID="$2"
      shift 2
      ;;
    --reuse-apk)
      REUSE_APK=1
      shift
      ;;
    --skip-install)
      SKIP_INSTALL=1
      REUSE_APK=1
      shift
      ;;
    --width)
      WIDTH="$2"
      shift 2
      ;;
    --height)
      HEIGHT="$2"
      shift 2
      ;;
    --settle-seconds)
      SETTLE_SECONDS="$2"
      shift 2
      ;;
    --self-test)
      SELF_TEST=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ "$STATE" != "light" && "$STATE" != "dark" ]]; then
  echo "State must be 'light' or 'dark': $STATE" >&2
  exit 2
fi

if [[ -z "$PYTHON_BIN" ]]; then
  for candidate in python3 python py; do
    if command -v "$candidate" >/dev/null 2>&1 &&
      "$candidate" -c 'import json, sys' >/dev/null 2>&1; then
      PYTHON_BIN="$candidate"
      break
    fi
  done
fi
if [[ -z "$PYTHON_BIN" ]]; then
  echo "Python is required to read $MANIFEST" >&2
  exit 1
fi

read -r MANIFEST_WIDTH MANIFEST_HEIGHT MANIFEST_ANDROID_NAME < <(
  "$PYTHON_BIN" - "$MANIFEST" "$SCREEN" "$DEVICE_NAME" <<'PY'
import json
import sys
from pathlib import Path

manifest_path = Path(sys.argv[1])
screen_name = sys.argv[2]
device_name = sys.argv[3]
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

if screen_name not in manifest["screens"]:
    raise SystemExit(f"Unknown screen '{screen_name}'")

for device in manifest["device_matrix"]:
    if device["name"] == device_name:
        print(device["width"], device["height"], device["android"])
        break
else:
    raise SystemExit(f"Unknown device '{device_name}'")
PY
)

MANIFEST_WIDTH="${MANIFEST_WIDTH//$'\r'/}"
MANIFEST_HEIGHT="${MANIFEST_HEIGHT//$'\r'/}"
MANIFEST_ANDROID_NAME="${MANIFEST_ANDROID_NAME//$'\r'/}"
WIDTH="${WIDTH:-$MANIFEST_WIDTH}"
HEIGHT="${HEIGHT:-$MANIFEST_HEIGHT}"

if [[ "$SELF_TEST" == "1" ]]; then
  echo "Android fixture capture self-test OK: screen=$SCREEN state=$STATE device=$DEVICE_NAME android=$MANIFEST_ANDROID_NAME size=${WIDTH}x${HEIGHT}"
  exit 0
fi

if ! command -v "$ADB" >/dev/null 2>&1; then
  echo "adb is required for Android visual fixture capture." >&2
  exit 1
fi

cleanup_display() {
  "$ADB" shell wm size reset >/dev/null 2>&1 || true
  "$ADB" shell wm density reset >/dev/null 2>&1 || true
}
trap cleanup_display EXIT

dismiss_system_dialogs() {
  quiet_background_system_apps
  "$ADB" shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
  "$ADB" shell input keyevent KEYCODE_ESCAPE >/dev/null 2>&1 || true
}

quiet_background_system_apps() {
  adb_shell_bounded 5 pm disable-user --user 0 com.google.android.setupwizard >/dev/null 2>&1 || true
  adb_shell_bounded 5 am force-stop com.google.android.setupwizard >/dev/null 2>&1 || true
  adb_shell_bounded 5 pm disable-user --user 0 com.google.android.googlesdksetup >/dev/null 2>&1 || true
  adb_shell_bounded 5 am force-stop com.google.android.googlesdksetup >/dev/null 2>&1 || true
  "$ADB" shell cmd statusbar collapse >/dev/null 2>&1 || true
}

focused_window_snapshot() {
  {
    adb_shell_bounded 5 dumpsys activity activities
    adb_shell_bounded 5 dumpsys window
  } |
  grep -E 'mCurrentFocus|mFocusedApp|mFocusedWindow|topResumedActivity|ResumedActivity|mKeyguardShowing|isKeyguardShowing|isSleeping|mDreamingLockscreen|mVisible=|mVisibleRequested=|mClientVisible=|state=|mHasSurface|isReadyForDisplay|Application Error|AppErrorDialog|ErrorDialog|Application Not Responding|ANR in|isn.t responding' |
    head -n 60 |
    tr -d '\r' || true
}

dump_debug_state() {
  local label="$1"
  local debug_dir="$DEBUG_ROOT/$DEVICE_NAME/$STATE"
  local prefix="$debug_dir/$SCREEN-$label"

  mkdir -p "$debug_dir"
  log "Writing Android visual debug state: $prefix"
  {
    echo "screen=$SCREEN"
    echo "state=$STATE"
    echo "device=$DEVICE_NAME"
    echo "package=$PACKAGE_ID"
    echo
    echo "== focused window snapshot =="
    focused_window_snapshot
    echo
    echo "== pid =="
    adb_shell_bounded 5 pidof "$PACKAGE_ID"
    echo
    echo "== display =="
    adb_shell_bounded 5 wm size
    adb_shell_bounded 5 wm density
    echo
    echo "== resolved launch activity =="
    resolve_launch_activity
  } > "$prefix-summary.txt" 2>&1 || true

  adb_shell_bounded 10 dumpsys activity activities > "$prefix-activity.txt" 2>&1 || true
  adb_shell_bounded 10 dumpsys window > "$prefix-window.txt" 2>&1 || true
  "$ADB" logcat -d -t 2000 > "$prefix-logcat.txt" 2>&1 || true
  adb_shell_bounded 10 sh -c 'ls -la /data/anr 2>/dev/null; for trace in /data/anr/*; do [ -f "$trace" ] || continue; echo "===== $trace ====="; cat "$trace"; done' > "$prefix-anr-traces.txt" 2>&1 || true
  "$ADB" exec-out screencap -p > "$prefix-screencap.png" 2>/dev/null || true
  adb_shell_bounded 10 uiautomator dump /sdcard/hermex-window.xml >/dev/null 2>&1 || true
  "$ADB" exec-out cat /sdcard/hermex-window.xml > "$prefix-uiautomator.xml" 2>/dev/null || true
}

adb_shell_bounded() {
  local seconds="$1"
  shift
  perl -e 'alarm shift; exec @ARGV' "$seconds" "$ADB" shell "$@" 2>/dev/null || true
}

wait_for_window_service() {
  local service_state
  for attempt in {1..60}; do
    service_state="$(adb_shell_bounded 5 service check window | tr -d '\r')"
    if grep -Eqi 'found|window:' <<<"$service_state"; then
      return 0
    fi
    if (( attempt == 1 || attempt % 10 == 0 )); then
      log "Waiting for Android window service (attempt $attempt)"
      [[ -n "$service_state" ]] && echo "$service_state" >&2
    fi
    sleep 2
  done

  echo "Android window service was not ready after emulator boot." >&2
  adb_shell_bounded 5 service list >&2 || true
  return 1
}

prepare_android_emulator_for_capture() {
  log "Provisioning Android emulator for unattended visual capture"
  adb_shell_bounded 5 settings put global device_provisioned 1 >/dev/null 2>&1 || true
  adb_shell_bounded 5 settings put global setup_wizard_has_run 1 >/dev/null 2>&1 || true
  adb_shell_bounded 5 settings put secure user_setup_complete 1 >/dev/null 2>&1 || true
  adb_shell_bounded 5 settings put secure lockscreen.disabled 1 >/dev/null 2>&1 || true
  adb_shell_bounded 5 settings put secure lock_screen_locking_enabled 0 >/dev/null 2>&1 || true
  adb_shell_bounded 5 settings put secure screensaver_enabled 0 >/dev/null 2>&1 || true
  adb_shell_bounded 5 settings put secure screensaver_activate_on_sleep 0 >/dev/null 2>&1 || true
  adb_shell_bounded 5 settings put secure screensaver_activate_on_dock 0 >/dev/null 2>&1 || true
  adb_shell_bounded 5 settings put secure doze_enabled 0 >/dev/null 2>&1 || true
  adb_shell_bounded 5 settings put system screen_off_timeout 2147483647 >/dev/null 2>&1 || true
  adb_shell_bounded 5 svc power stayon true >/dev/null 2>&1 || true
  quiet_background_system_apps
}

has_hermex_focus() {
  local snapshot="$1"
  grep -E "mCurrentFocus=.*$PACKAGE_ID|mFocusedWindow=.*$PACKAGE_ID" <<<"$snapshot" >/dev/null
}

is_keyguard_blocking_capture() {
  local snapshot="$1"
  grep -Eqi 'mKeyguardShowing=true|isKeyguardShowing=true|isSleeping=true|mDreamingLockscreen=true' <<<"$snapshot"
}

is_blocking_system_window() {
  local snapshot="$1"
  if grep -Eqi 'Application Error|AppErrorDialog|ErrorDialog' <<<"$snapshot"; then
    return 0
  fi
  grep -Eqi "Application Not Responding|ANR in|isn.t responding" <<<"$snapshot"
}

unlock_device_for_capture() {
  log "Waking and unlocking Android emulator"
  prepare_android_emulator_for_capture
  adb_shell_bounded 5 input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb_shell_bounded 5 input keyevent 224 >/dev/null 2>&1 || true
  adb_shell_bounded 5 wm dismiss-keyguard >/dev/null 2>&1 || true
  adb_shell_bounded 5 input keyevent KEYCODE_MENU >/dev/null 2>&1 || true
  adb_shell_bounded 5 input keyevent 82 >/dev/null 2>&1 || true
  adb_shell_bounded 5 input swipe "$((WIDTH / 2))" "$((HEIGHT * 3 / 4))" "$((WIDTH / 2))" "$((HEIGHT / 4))" 300 >/dev/null 2>&1 || true
  adb_shell_bounded 5 cmd statusbar collapse >/dev/null 2>&1 || true

  local focus
  for attempt in {1..20}; do
    focus="$(focused_window_snapshot)"
    if ! is_keyguard_blocking_capture "$focus"; then
      return 0
    fi
    if (( attempt == 1 || attempt % 5 == 0 )); then
      log "Waiting for keyguard to clear before capture (attempt $attempt)"
      echo "$focus" >&2
    fi
    prepare_android_emulator_for_capture
    adb_shell_bounded 5 input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
    adb_shell_bounded 5 input keyevent 224 >/dev/null 2>&1 || true
    adb_shell_bounded 5 wm dismiss-keyguard >/dev/null 2>&1 || true
    adb_shell_bounded 5 input keyevent KEYCODE_MENU >/dev/null 2>&1 || true
    adb_shell_bounded 5 input keyevent 82 >/dev/null 2>&1 || true
    sleep 1
  done

  echo "Android keyguard did not clear before capture." >&2
  focused_window_snapshot >&2
  return 1
}

resolve_launch_activity() {
  "$ADB" shell cmd package resolve-activity \
    --brief \
    -a android.intent.action.MAIN \
    -c android.intent.category.LAUNCHER \
    -p "$PACKAGE_ID" 2>/dev/null |
    tr -d '\r' |
    awk '/\// { line = $0 } END { print line }'
}

launch_app() {
  local activity
  activity="$(resolve_launch_activity)"
  if [[ -n "$activity" ]]; then
    "$ADB" shell am start -W \
      -n "$activity" \
      -a android.intent.action.MAIN \
      -c android.intent.category.LAUNCHER >/dev/null
  else
    "$ADB" shell monkey -p "$PACKAGE_ID" -c android.intent.category.LAUNCHER 1 >/dev/null
  fi
}

write_visual_fixture_selection() {
  local app_dir="/data/data/$PACKAGE_ID"
  local fallback_app_dir="/data/user/0/$PACKAGE_ID"
  local write_command="cd '$app_dir' 2>/dev/null || cd '$fallback_app_dir' || exit 1; mkdir -p files; cat > 'files/$RUNTIME_FIXTURE_FILE'"
  local remote_command="run-as $PACKAGE_ID sh -c \"$write_command\""

  log "Writing runtime visual fixture selector: $SCREEN"
  if ! printf '%s' "$SCREEN" | "$ADB" shell "$remote_command"; then
    echo "Could not write runtime fixture selector with run-as for $PACKAGE_ID." >&2
    echo "The visual APK must be a debuggable Skip build so CI can select screens at runtime." >&2
    return 1
  fi
}

wait_for_app_focus() {
  local pid
  local focus
  for attempt in {1..30}; do
    if (( attempt == 1 || attempt % 5 == 0 )); then
      log "Hermex focus check attempt $attempt"
    fi
    pid="$(adb_shell_bounded 5 pidof "$PACKAGE_ID" | tr -d '\r[:space:]')"
    if [[ -n "$pid" ]]; then
      focus="$(focused_window_snapshot)"
      if has_hermex_focus "$focus" && ! is_keyguard_blocking_capture "$focus"; then
        return 0
      fi
      if (( attempt == 1 || attempt % 5 == 0 )); then
        echo "Hermex process is running but not ready for screenshot yet:" >&2
        echo "$focus" >&2
      fi
      if is_keyguard_blocking_capture "$focus"; then
        unlock_device_for_capture >/dev/null 2>&1 || true
      fi
    fi
    if (( attempt == 5 || attempt == 10 || attempt == 20 )); then
      dismiss_system_dialogs
      launch_app
    fi
    sleep 1
  done

  echo "Hermex process did not start after launch." >&2
  focused_window_snapshot >&2
  dump_debug_state "focus-timeout"
  return 1
}

assert_hermex_focus_for_screenshot() {
  local pid
  local focus
  pid="$(adb_shell_bounded 5 pidof "$PACKAGE_ID" | tr -d '\r[:space:]')"
  if [[ -z "$pid" ]]; then
    echo "Hermex process was not running at screenshot time." >&2
    focused_window_snapshot >&2
    return 1
  fi
  focus="$(focused_window_snapshot)"
  if is_blocking_system_window "$focus"; then
    echo "Screenshot was blocked by a system/ANR dialog; refusing to upload it as a Hermex screen." >&2
    echo "$focus" >&2
    return 1
  fi
  if is_keyguard_blocking_capture "$focus"; then
    echo "Screenshot was blocked by Android keyguard/sleep state; refusing to upload it as a Hermex screen." >&2
    echo "$focus" >&2
    return 1
  fi
  if ! has_hermex_focus "$focus"; then
    echo "Hermex process is running but the focused Android window is not Hermex; refusing to upload this capture." >&2
    echo "$focus" >&2
    return 1
  fi
}

capture_verified_screenshot() {
  local screenshot_path="$1"
  local attempt

  for attempt in $(seq 1 "$CAPTURE_ATTEMPTS"); do
    if (( attempt > 1 )); then
      log "Retrying screenshot capture after emulator frame/focus guard failure (attempt $attempt/$CAPTURE_ATTEMPTS)"
      dismiss_system_dialogs
      launch_app
      if ! wait_for_app_focus; then
        dump_debug_state "attempt-$attempt-refocus"
        continue
      fi
      sleep "$CAPTURE_RETRY_SECONDS"
    fi

    log "Capturing screenshot to $screenshot_path"
    "$ADB" exec-out screencap -p > "$screenshot_path"

    if [[ ! -s "$screenshot_path" ]]; then
      echo "Screenshot capture produced an empty file: $screenshot_path" >&2
      continue
    fi

    if ! assert_hermex_focus_for_screenshot; then
      dump_debug_state "attempt-$attempt-focus"
      continue
    fi

    log "Inspecting screenshot pixels"
    if "$PYTHON_BIN" "$ROOT/ci/assert_android_capture_not_system_dialog.py" "$screenshot_path"; then
      return 0
    fi
    dump_debug_state "attempt-$attempt-pixels"
  done

  echo "Recent Android logcat lines after failed Hermex screenshot inspection:" >&2
  adb_shell_bounded 10 logcat -d -t 300 |
    grep -Ei 'hermex|skip|fatal|exception|androidruntime|crash|mainactivity' >&2 || true
  return 1
}

if [[ "$REUSE_APK" != "1" ]]; then
  log "Building Skip Android visual fixture APK for screen=$SCREEN"
  export HERMEX_ALLOW_INCOMPLETE_SKIP_APK=1
  export HERMEX_VISUAL_FIXTURE_NAME="$SCREEN"
  bash "$ROOT/ci/build_skip_android_app.sh" "$APK_DIR"
else
  log "Reusing existing APK from $APK_DIR"
fi

APK_PATH=""
if [[ "$SKIP_INSTALL" != "1" ]]; then
  APK_PATH="$(find "$APK_DIR" -type f -name '*.apk' | sort | head -n 1)"
  if [[ -z "$APK_PATH" ]]; then
    echo "No APK was produced in $APK_DIR" >&2
    exit 1
  fi
  log "Using APK: $APK_PATH ($(du -h "$APK_PATH" | awk '{ print $1 }'))"
else
  log "Reusing already installed Hermex app package $PACKAGE_ID"
fi

log "Waiting for Android device"
"$ADB" wait-for-device
wait_for_window_service
prepare_android_emulator_for_capture
log "Configuring emulator display ${WIDTH}x${HEIGHT}"
"$ADB" shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
"$ADB" shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
"$ADB" shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
"$ADB" shell wm size "${WIDTH}x${HEIGHT}" >/dev/null
"$ADB" shell wm density "${HERMEX_VISUAL_DENSITY:-160}" >/dev/null

if [[ "$STATE" == "dark" ]]; then
  "$ADB" shell cmd uimode night yes >/dev/null 2>&1 || true
else
  "$ADB" shell cmd uimode night no >/dev/null 2>&1 || true
fi
unlock_device_for_capture

if [[ "$SKIP_INSTALL" != "1" ]]; then
  log "Installing Hermex APK"
  "$ADB" install -r "$APK_PATH" >/dev/null
fi
log "Clearing Hermex app data"
"$ADB" shell pm clear "$PACKAGE_ID" >/dev/null 2>&1 || true
write_visual_fixture_selection
dismiss_system_dialogs
unlock_device_for_capture
log "Launching Hermex"
launch_app
log "Waiting for Hermex focus"
wait_for_app_focus
sleep "$SETTLE_SECONDS"
quiet_background_system_apps

if [[ "$SCREEN" == "chat-keyboard-open" ]]; then
  "$ADB" shell input tap "$((WIDTH / 2))" "$((HEIGHT - 128))" >/dev/null 2>&1 || true
  sleep 2
fi

SCREENSHOT_PATH="$OUTPUT_ROOT/$DEVICE_NAME/$STATE/$SCREEN.png"
mkdir -p "$(dirname "$SCREENSHOT_PATH")"
capture_verified_screenshot "$SCREENSHOT_PATH"

echo "$SCREENSHOT_PATH"
