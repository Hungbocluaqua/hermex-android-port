#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/ci/visual-goldens/hermex-screens.json"
STATE="${HERMEX_VISUAL_STATE:-dark}"
DEVICE_NAME="${HERMEX_VISUAL_DEVICE:-compact-phone}"
OUTPUT_ROOT="${HERMEX_VISUAL_OUTPUT_ROOT:-"$ROOT/dist/android-visual-screens"}"
APK_DIR="${HERMEX_VISUAL_APK_DIR:-"${RUNNER_TEMP:-"$ROOT/.build"}/hermex-skip-visual-apk"}"
PACKAGE_ID="${HERMEX_SKIP_APP_ID:-com.uzairansar.hermex}"
ADB="${ADB:-adb}"
SCREEN_LIST="${HERMEX_VISUAL_SCREENS:-all}"
PYTHON_BIN="${PYTHON_BIN:-}"
REUSE_APK=0

usage() {
  cat <<'USAGE'
Usage: capture_skip_android_fixture_matrix.sh [options]

Capture multiple Hermex visual fixtures from one connected Android emulator.
The script builds one reusable Skip APK unless --reuse-apk is passed, then
selects each screen at runtime before launching the app.

Options:
  --screens LIST       Comma-separated fixture names, or "all"
  --state light|dark   Android system UI mode to capture
  --device-name NAME   Device key from ci/visual-goldens/hermex-screens.json
  --output-root DIR    Screenshot artifact root
  --apk-dir DIR        Temporary Skip APK build output directory
  --package-id ID      Android package id
  --reuse-apk          Use an existing reusable APK from --apk-dir
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --screens)
      SCREEN_LIST="$2"
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

mapfile -t SCREENS < <(
  "$PYTHON_BIN" - "$MANIFEST" "$SCREEN_LIST" <<'PY'
import json
import sys
from pathlib import Path

manifest = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
requested = sys.argv[2].strip()
available = manifest["screens"]

if requested == "all":
    selected = available
else:
    selected = [item.strip() for item in requested.split(",") if item.strip()]
    unknown = [item for item in selected if item not in available]
    if unknown:
        raise SystemExit(f"Unknown visual fixture(s): {', '.join(unknown)}")

for screen in selected:
    print(screen)
PY
)

if [[ "${#SCREENS[@]}" -eq 0 ]]; then
  echo "No visual fixture screens selected." >&2
  exit 2
fi

if [[ "$REUSE_APK" != "1" ]]; then
  echo "Building one reusable Skip Android visual APK for ${#SCREENS[@]} screen(s)." >&2
  export HERMEX_ALLOW_INCOMPLETE_SKIP_APK=1
  unset HERMEX_VISUAL_FIXTURE_NAME
  bash "$ROOT/ci/build_skip_android_app.sh" "$APK_DIR"
else
  echo "Reusing existing APK from $APK_DIR for ${#SCREENS[@]} screen(s)." >&2
fi

echo "Waiting for Android emulator boot before fixture capture." >&2
"$ADB" wait-for-device
booted=""
for _ in {1..180}; do
  booted="$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  if [[ "$booted" == "1" ]]; then
    echo "Android emulator boot completed." >&2
    break
  fi
  sleep 5
done
if [[ "$booted" != "1" ]]; then
  echo "Android emulator did not finish booting." >&2
  exit 1
fi

first_screen=1
for screen in "${SCREENS[@]}"; do
  args=(
    --screen "$screen"
    --state "$STATE"
    --device-name "$DEVICE_NAME"
    --output-root "$OUTPUT_ROOT"
    --apk-dir "$APK_DIR"
    --package-id "$PACKAGE_ID"
    --reuse-apk
  )
  if [[ "$first_screen" != "1" ]]; then
    args+=(--skip-install)
  fi
  bash "$ROOT/ci/capture_skip_android_fixture.sh" "${args[@]}"
  first_screen=0
done
