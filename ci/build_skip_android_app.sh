#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="${SKIP_APP_WORKDIR:-"${RUNNER_TEMP:-"$ROOT/.build"}/hermex-skip-app"}"
DIST_DIR="${1:-"$ROOT/dist/skip-android"}"
APP_VERSION="${HERMEX_SKIP_VERSION:-0.3.0}"
APP_ID="${HERMEX_SKIP_APP_ID:-com.uzairansar.hermex}"
MODULE_NAME="HermexSkipApp"
APP_PARENT="$(dirname "$APP_DIR")"
APP_PROJECT_NAME="$(basename "$APP_DIR")"

rm -rf "$APP_DIR" "$DIST_DIR"
mkdir -p "$APP_PARENT" "$DIST_DIR"

(
  cd "$APP_PARENT"
  skip init --transpiled-app --appid="$APP_ID" --version="$APP_VERSION" "$APP_PROJECT_NAME" "$MODULE_NAME"
)
python3 "$ROOT/ci/prepare_skip_hermex_app.py" \
  --app-dir "$APP_DIR" \
  --repo-root "$ROOT" \
  --module-name "$MODULE_NAME"

(
  cd "$APP_DIR"
  swift package resolve
  swift build --target "$MODULE_NAME"
  skip export --debug --dir "$DIST_DIR"
)

mapfile -t artifacts < <(find "$DIST_DIR" -type f \( -name '*.apk' -o -name '*.aab' \) | sort)
if [[ "${#artifacts[@]}" -eq 0 ]]; then
  echo "Skip export did not produce an APK or AAB under $DIST_DIR" >&2
  exit 1
fi

printf '%s\n' "${artifacts[@]}"
