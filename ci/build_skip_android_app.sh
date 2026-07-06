#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="${SKIP_APP_WORKDIR:-"${RUNNER_TEMP:-"$ROOT/.build"}/hermex-skip-app"}"
DIST_DIR="${1:-"$ROOT/dist/skip-android"}"
APP_VERSION="${HERMEX_SKIP_VERSION:-0.3.1}"
APP_ID="${HERMEX_SKIP_APP_ID:-com.uzairansar.hermex}"
MODULE_NAME="HermexSkipApp"
APP_PARENT="$(dirname "$APP_DIR")"
APP_PROJECT_NAME="$(basename "$APP_DIR")"

rm -rf "$APP_DIR" "$DIST_DIR"
mkdir -p "$APP_PARENT" "$DIST_DIR"

if [[ "${HERMEX_ALLOW_INCOMPLETE_SKIP_APK:-0}" != "1" ]]; then
  python3 "$ROOT/ci/skip_release_readiness_audit.py"
fi

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

while IFS= read -r -d '' build_file; do
  if grep -q '^android[[:space:]]*{' "$build_file" &&
    ! grep -q 'com.android.library\|android.library' "$build_file"; then
    if grep -q '^plugins[[:space:]]*{' "$build_file"; then
      perl -0pi -e 's/(plugins[[:space:]]*\{\n)/$1    alias(libs.plugins.android.library)\n/s' "$build_file"
    else
      tmp_file="${build_file}.tmp"
      {
        printf 'plugins {\n    alias(libs.plugins.android.library)\n}\n\n'
        cat "$build_file"
      } > "$tmp_file"
      mv "$tmp_file" "$build_file"
    fi
    echo "Patched Android library plugin into $build_file"
  fi

  if grep -q '^android[[:space:]]*{' "$build_file" &&
    ! grep -q 'compileSdk[[:space:]]*=' "$build_file"; then
    perl -0pi -e 's/(android[[:space:]]*\{\n)/$1    compileSdk = libs.versions.android.sdk.compile.get().toInt()\n    compileOptions {\n        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())\n        targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())\n    }\n    defaultConfig {\n        minSdk = libs.versions.android.sdk.min.get().toInt()\n    }\n/s' "$build_file"
    echo "Patched Android SDK settings into $build_file"
  fi
done < <(find "$APP_DIR/.build/plugins/outputs" -name 'build.gradle.kts' -print0)

GRADLE_SETTINGS=""
GRADLE_TASK="assembleDebug"
if [[ -f "$APP_DIR/Android/settings.gradle.kts" ]]; then
  GRADLE_SETTINGS="$APP_DIR/Android/settings.gradle.kts"
  GRADLE_TASK=":app:assembleDebug"
else
  GRADLE_SETTINGS="$(find "$APP_DIR/.build/plugins/outputs" -path "*/$MODULE_NAME/destination/skipstone/settings.gradle.kts" -print -quit)"
fi

if [[ -z "$GRADLE_SETTINGS" ]]; then
  echo "Could not locate generated Skip Gradle project for $MODULE_NAME" >&2
  find "$APP_DIR" -maxdepth 5 \( -name 'settings.gradle.kts' -o -name 'gradlew' \) -print >&2 || true
  exit 1
fi
GRADLE_DIR="$(dirname "$GRADLE_SETTINGS")"

patch_android_branding() {
  local search_roots=("$APP_DIR" "$GRADLE_DIR")
  [[ -d "$APP_DIR/.build/Android" ]] && search_roots+=("$APP_DIR/.build/Android")
  [[ -d "$APP_DIR/.build/plugins/outputs" ]] && search_roots+=("$APP_DIR/.build/plugins/outputs")

  while IFS= read -r -d '' manifest; do
    local main_dir res_dir
    main_dir="$(dirname "$manifest")"
    res_dir="$main_dir/res"
    mkdir -p "$res_dir/values" "$res_dir/drawable" "$res_dir/drawable-nodpi" "$res_dir/mipmap-anydpi-v26"

    local icon_ref round_icon_ref icon_source
    icon_ref="@mipmap/ic_launcher"
    round_icon_ref="@mipmap/ic_launcher_round"
    icon_source="$ROOT/HermesMobile/Resources/Assets.xcassets/AppIcon.appiconset/hermes_mobile_dark_icon.png"
    [[ -f "$icon_source" ]] || icon_source="$ROOT/Sources/HermexUI/Resources/Logo/HermesAppIcon.png"
    if [[ -f "$icon_source" ]]; then
      cp "$icon_source" "$res_dir/drawable-nodpi/hermex_app_icon.png"
      icon_ref="@drawable/hermex_app_icon"
      round_icon_ref="@drawable/hermex_app_icon"
    fi

    ICON_REF="$icon_ref" ROUND_ICON_REF="$round_icon_ref" perl -0pi -e 's/android:label="[^"]*"/android:label="Hermex"/g; s/android:icon="[^"]*"/android:icon="$ENV{ICON_REF}"/g; s/android:roundIcon="[^"]*"/android:roundIcon="$ENV{ROUND_ICON_REF}"/g' "$manifest"
    if ! grep -q 'android:label=' "$manifest"; then
      perl -0pi -e 's/<application\b/<application android:label="Hermex"/' "$manifest"
    fi
    if ! grep -q 'android:icon=' "$manifest"; then
      ICON_REF="$icon_ref" perl -0pi -e 's/<application\b/<application android:icon="$ENV{ICON_REF}"/' "$manifest"
    fi
    if ! grep -q 'android:roundIcon=' "$manifest"; then
      ROUND_ICON_REF="$round_icon_ref" perl -0pi -e 's/<application\b/<application android:roundIcon="$ENV{ROUND_ICON_REF}"/' "$manifest"
    fi

    if [[ -f "$res_dir/values/strings.xml" ]]; then
      perl -0pi -e 's/(<string\s+name="app_name">)[^<]*(<\/string>)/${1}Hermex${2}/g' "$res_dir/values/strings.xml"
    else
      cat > "$res_dir/values/strings.xml" <<'XML'
<resources>
    <string name="app_name">Hermex</string>
</resources>
XML
    fi

    cat > "$res_dir/values/hermex_launcher.xml" <<'XML'
<resources>
    <color name="hermex_launcher_background">#000000</color>
</resources>
XML

    cat > "$res_dir/drawable/ic_launcher_foreground.xml" <<'XML'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#2D2500"
        android:pathData="M20,24h18v64h-18z M70,24h18v64h-18z M20,49h68v16h-68z" />
    <path
        android:fillColor="#F8D84A"
        android:pathData="M18,20h18v64h-18z M72,20h18v64h-18z M18,47h72v16h-72z" />
    <path
        android:fillColor="#FFF2A6"
        android:pathData="M22,24h10v8h-10z M76,24h10v8h-10z M22,51h64v4h-64z" />
</vector>
XML

    cat > "$res_dir/mipmap-anydpi-v26/ic_launcher.xml" <<'XML'
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/hermex_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
XML

    cat > "$res_dir/mipmap-anydpi-v26/ic_launcher_round.xml" <<'XML'
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/hermex_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
XML

    echo "Patched Android branding into $main_dir"
  done < <(find "${search_roots[@]}" -path '*/src/main/AndroidManifest.xml' -print0)

  while IFS= read -r -d '' strings_file; do
    if grep -q '<string[[:space:]]\+name="app_name">' "$strings_file"; then
      perl -0pi -e 's/(<string\s+name="app_name">)[^<]*(<\/string>)/${1}Hermex${2}/g' "$strings_file"
    fi
  done < <(find "$APP_DIR" -path '*/src/main/res/values/*.xml' -print0)
}

patch_android_branding

dump_generated_kotlin_diagnostics() {
  echo "::group::Generated Kotlin diagnostics"
  while IFS= read -r -d '' kotlin_file; do
    echo "--- $kotlin_file"
    nl -ba "$kotlin_file" | sed -n '1,120p'
    case "$kotlin_file" in
      *HermexDTOs.kt)
        nl -ba "$kotlin_file" | sed -n '1660,1725p'
        ;;
      *HermexStateMapping.kt)
        nl -ba "$kotlin_file" | sed -n '1,220p'
        ;;
      *HermexSharedModels.kt)
        nl -ba "$kotlin_file" | sed -n '460,520p'
        ;;
    esac
  done < <(
    find "$APP_DIR/.build/plugins/outputs" "$APP_DIR/.build/Android" \
      -type f \( \
        -name 'HermexJSONValue.kt' -o \
        -name 'HermexDTOs.kt' -o \
        -name 'HermexStateMapping.kt' -o \
        -name 'HermexSharedModels.kt' \
      \) -print0
  )
  echo "::endgroup::"
}

if ! (
  cd "$GRADLE_DIR"
  if command -v skip >/dev/null 2>&1; then
    skip gradle -p "$GRADLE_DIR" "$GRADLE_TASK"
  elif [[ -f ./gradlew ]]; then
    chmod +x ./gradlew
    ./gradlew --no-daemon "$GRADLE_TASK"
  elif command -v gradle >/dev/null 2>&1; then
    gradle --no-daemon "$GRADLE_TASK"
  else
    echo "Could not locate skip, Gradle wrapper in $GRADLE_DIR, or system gradle" >&2
    find "$APP_DIR" -maxdepth 5 \( -name 'settings.gradle.kts' -o -name 'gradlew' \) -print >&2 || true
    exit 1
  fi
); then
  dump_generated_kotlin_diagnostics
  exit 1
fi

find "$GRADLE_DIR" "$APP_DIR/.build/Android" "$APP_DIR/.build/plugins/outputs" -type f \( -name '*.apk' -o -name '*.aab' \) -print0 |
  while IFS= read -r -d '' artifact; do
    cp "$artifact" "$DIST_DIR/$(basename "$artifact")"
  done

artifacts_list="$DIST_DIR/artifacts.txt"
find "$DIST_DIR" -type f \( -name '*.apk' -o -name '*.aab' \) | sort > "$artifacts_list"
if [[ ! -s "$artifacts_list" ]]; then
  echo "The generated Skip Gradle project did not produce an APK or AAB under $DIST_DIR" >&2
  exit 1
fi

cat "$artifacts_list"
