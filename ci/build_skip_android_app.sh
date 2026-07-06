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
)

GRADLE_SETTINGS=""
if [[ -f "$APP_DIR/Android/settings.gradle.kts" ]]; then
  GRADLE_SETTINGS="$APP_DIR/Android/settings.gradle.kts"
else
  GRADLE_SETTINGS="$(find "$APP_DIR/.build/plugins/outputs" -path "*/$MODULE_NAME/destination/skipstone/settings.gradle.kts" -print -quit)"
fi

if [[ -z "$GRADLE_SETTINGS" ]]; then
  echo "Could not locate generated Skip Gradle project for $MODULE_NAME" >&2
  find "$APP_DIR" -maxdepth 5 \( -name 'settings.gradle.kts' -o -name 'gradlew' \) -print >&2 || true
  exit 1
fi
GRADLE_DIR="$(dirname "$GRADLE_SETTINGS")"

(
  cd "$GRADLE_DIR"
  if [[ -f ./gradlew ]]; then
    chmod +x ./gradlew
    ./gradlew --no-daemon assembleDebug
  elif command -v gradle >/dev/null 2>&1; then
    gradle --no-daemon assembleDebug
  else
    echo "Could not locate Gradle wrapper in $GRADLE_DIR and no system gradle is installed" >&2
    find "$APP_DIR" -maxdepth 5 \( -name 'settings.gradle.kts' -o -name 'gradlew' \) -print >&2 || true
    exit 1
  fi
)

find "$GRADLE_DIR" "$APP_DIR/.build/plugins/outputs" -type f \( -name '*.apk' -o -name '*.aab' \) -print0 |
  while IFS= read -r -d '' artifact; do
    cp "$artifact" "$DIST_DIR/$(basename "$artifact")"
  done

mapfile -t artifacts < <(find "$DIST_DIR" -type f \( -name '*.apk' -o -name '*.aab' \) | sort)
if [[ "${#artifacts[@]}" -eq 0 ]]; then
  echo "The generated Skip Gradle project did not produce an APK or AAB under $DIST_DIR" >&2
  exit 1
fi

printf '%s\n' "${artifacts[@]}"
