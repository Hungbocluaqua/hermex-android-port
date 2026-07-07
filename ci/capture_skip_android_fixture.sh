#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/ci/visual-goldens/hermex-screens.json"
SCREEN="${HERMEX_VISUAL_FIXTURE_NAME:-session-list}"
STATE="${HERMEX_VISUAL_STATE:-dark}"
DEVICE_NAME="${HERMEX_VISUAL_DEVICE:-compact-phone}"
OUTPUT_ROOT="${HERMEX_VISUAL_OUTPUT_ROOT:-"$ROOT/dist/android-visual-screens"}"
APK_DIR="${HERMEX_VISUAL_APK_DIR:-"${RUNNER_TEMP:-"$ROOT/.build"}/hermex-skip-visual-apk"}"
PACKAGE_ID="${HERMEX_SKIP_APP_ID:-com.uzairansar.hermex}"
ADB="${ADB:-adb}"
SETTLE_SECONDS="${HERMEX_VISUAL_SETTLE_SECONDS:-5}"
REUSE_APK=0
WIDTH=""
HEIGHT=""
SELF_TEST=0
PYTHON_BIN="${PYTHON_BIN:-}"

log() {
  printf '[%s] %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*" >&2
}

usage() {
  cat <<'USAGE'
Usage: capture_skip_android_fixture.sh [options]

Build a Skip Android APK for one Hermex visual fixture, install it on the
currently connected emulator, and capture:
  <output-root>/<device-name>/<state>/<screen>.png

Options:
  --screen NAME        Fixture screen from ci/visual-goldens/hermex-screens.json
  --state light|dark   Android system UI mode to capture
  --device-name NAME   Device key from ci/visual-goldens/hermex-screens.json
  --output-root DIR    Screenshot artifact root
  --apk-dir DIR        Temporary Skip APK build output directory
  --package-id ID      Android package id
  --reuse-apk          Install an existing APK from --apk-dir instead of rebuilding
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
  "$ADB" shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
  "$ADB" shell input keyevent KEYCODE_ESCAPE >/dev/null 2>&1 || true
  "$ADB" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  "$ADB" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  "$ADB" shell am force-stop com.google.android.apps.nexuslauncher >/dev/null 2>&1 || true
  "$ADB" shell am force-stop com.android.launcher3 >/dev/null 2>&1 || true
  "$ADB" shell cmd statusbar collapse >/dev/null 2>&1 || true
}

focused_window_snapshot() {
  "$ADB" shell dumpsys -t 3 window 2>/dev/null |
    grep -E 'mCurrentFocus|mFocusedApp|Application Error|AppErrorDialog|ErrorDialog|ANR|isn.t responding|com.android.systemui' |
    head -n 30 |
    tr -d '\r' || true
}

is_blocking_system_window() {
  local snapshot="$1"
  grep -Eqi 'Application Error|AppErrorDialog|ErrorDialog|ANR|isn.t responding|mCurrentFocus.*com.android.systemui|mFocusedApp.*com.android.systemui' <<<"$snapshot"
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

wait_for_app_focus() {
  local focus
  for attempt in {1..24}; do
    if (( attempt == 1 || attempt % 6 == 0 )); then
      log "Hermex focus check attempt $attempt"
    fi
    focus="$(focused_window_snapshot)"
    if is_blocking_system_window "$focus"; then
      dismiss_system_dialogs
      launch_app
      sleep 1
      continue
    fi
    if grep -q "$PACKAGE_ID" <<<"$focus"; then
      return 0
    fi
    if grep -Eqi 'Launcher' <<<"$focus"; then
      dismiss_system_dialogs
      launch_app
    fi
    sleep 1
  done

  echo "Hermex did not become the focused Android window." >&2
  focused_window_snapshot >&2
  return 1
}

assert_hermex_focus_for_screenshot() {
  local focus
  focus="$(focused_window_snapshot)"
  if is_blocking_system_window "$focus"; then
    echo "Screenshot was blocked by a system/ANR dialog; refusing to upload it as a Hermex screen." >&2
    echo "$focus" >&2
    return 1
  fi
  if ! grep -q "$PACKAGE_ID" <<<"$focus"; then
    echo "Screenshot was not captured with Hermex focused; refusing to upload a system-dialog image." >&2
    echo "$focus" >&2
    return 1
  fi
}

if [[ "$REUSE_APK" != "1" ]]; then
  log "Building Skip Android visual fixture APK for screen=$SCREEN"
  export HERMEX_ALLOW_INCOMPLETE_SKIP_APK=1
  export HERMEX_VISUAL_FIXTURE_NAME="$SCREEN"
  bash "$ROOT/ci/build_skip_android_app.sh" "$APK_DIR"
else
  log "Reusing existing APK from $APK_DIR"
fi

APK_PATH="$(find "$APK_DIR" -type f -name '*.apk' | sort | head -n 1)"
if [[ -z "$APK_PATH" ]]; then
  echo "No APK was produced in $APK_DIR" >&2
  exit 1
fi
log "Using APK: $APK_PATH ($(du -h "$APK_PATH" | awk '{ print $1 }'))"

log "Waiting for Android device"
"$ADB" wait-for-device
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

log "Installing Hermex APK"
"$ADB" install -r "$APK_PATH" >/dev/null
log "Clearing Hermex app data"
"$ADB" shell pm clear "$PACKAGE_ID" >/dev/null 2>&1 || true
dismiss_system_dialogs
log "Launching Hermex"
launch_app
log "Waiting for Hermex focus"
wait_for_app_focus
sleep "$SETTLE_SECONDS"

if [[ "$SCREEN" == "chat-keyboard-open" ]]; then
  "$ADB" shell input tap "$((WIDTH / 2))" "$((HEIGHT - 128))" >/dev/null 2>&1 || true
  sleep 2
fi

SCREENSHOT_PATH="$OUTPUT_ROOT/$DEVICE_NAME/$STATE/$SCREEN.png"
mkdir -p "$(dirname "$SCREENSHOT_PATH")"
log "Capturing screenshot to $SCREENSHOT_PATH"
"$ADB" exec-out screencap -p > "$SCREENSHOT_PATH"

if [[ ! -s "$SCREENSHOT_PATH" ]]; then
  echo "Screenshot capture produced an empty file: $SCREENSHOT_PATH" >&2
  exit 1
fi

assert_hermex_focus_for_screenshot
log "Inspecting screenshot pixels"
"$PYTHON_BIN" "$ROOT/ci/assert_android_capture_not_system_dialog.py" "$SCREENSHOT_PATH"

echo "$SCREENSHOT_PATH"
