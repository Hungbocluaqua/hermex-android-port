# Android Skip Port

Hermex is moving the Android app to a Skip-first architecture. The iOS SwiftUI app is the source of truth for UI and behavior; Android should match the iOS goldens instead of using Android-inspired approximations.

## Source Boundaries

- `HermexCore` contains shared DTOs, endpoint builders, request construction, SSE parsing, multipart upload/transcribe builders, a transport-backed API client, repository facades, observable app store, and state contracts.
- `HermexPlatform` contains protocols and coordinator glue for storage, cookies, cache, files, voice, TTS, share intake, notifications, and platform shortcuts.
- `HermexUI` contains SwiftUI layout contracts plus the current shared top-level screen entrypoints:
  onboarding, session list, chat/composer, settings, workspace, git, and the tasks/skills/memory/insights panel shell.
- `HermexStoreRootScreen` is the shared SwiftUI/store bridge for Skip: it renders `HermexRootScreen` from `HermexAppStore` state and routes handled UI events back into shared Core actions.
- `Package.swift` is wired as a Skip package: `HermexCore`, `HermexPlatform`, and `HermexUI` use the `skipstone` plugin, `HermexUI` depends on `SkipUI`, and all three shared modules declare `mode: transpiled` under `Sources/*/Skip/skip.yml`.
- The current Kotlin Compose app remains a fallback until the Skip-generated Android app passes visual and contract acceptance.

## Current Shared Coverage

- Onboarding is now driven by shared state/actions for server URL, display name, password auth, sanitized custom headers, saved-server selection, connection test, login, and password clearing.
- Skip Android uses the live store (not the demo/preview store), mirrors store snapshots into `@State` for redraws, keeps local draft/password typing state, and reserves a measured composer bottom inset so transcript content does not sit under the composer.
- Android branding patches re-apply after Gradle assemble so the Hermex launcher icon/label are not clobbered by Skip-generated defaults.
- Chat has a shared SSE reducer for token, reasoning, tool-status, done, and error events so platform stream clients can feed one canonical transcript state.
- Composer configuration is shared for models, profiles, workspaces, and reasoning effort selection.
- Workspace and Git are shared through typed store actions: directory/file preview, status refresh, diff, stage, unstage, discard, fetch, pull, push, and commit message submission.
- Tasks, skills, memory, and insights use shared JSON mappers and panel routing, with platform shims still responsible for OS-specific persistence and presentation.

## Migration Rules

- Move SwiftUI screens and client logic into shared modules without changing server endpoints or JSON shapes.
- Put iOS-only APIs behind `HermexPlatform` protocols instead of deleting features.
- Keep secure data scoped by normalized server URL.
- Do not add a WebView shell or require server changes.
- Treat iOS screenshots as goldens and reject Android output that overlaps system bars, keyboard, composer, or unreadable transcript text.

## Verification

The `Skip Android Parity` workflow runs a full shared Swift package build, shared Swift tests, Android fallback builds, endpoint parity, migration audits, visual manifest validation, and an Android fixture-capture wiring self-test. The manual Skip toolchain job is the macOS lane for enabling generated Android verification after the SwiftUI targets are migrated into Skip-compatible modules.

The `Skip Android Named Release` workflow is the runnable Skip APK lane. It creates a temporary conventional Skip transpiled app with `skip init --transpiled-app`, patches that app to launch `HermexStoreRootScreen`, runs the Skip-enabled Swift build so `skipstone` generates the Android Gradle project, builds that generated project with `assembleDebug`, and uploads the resulting APK/AAB assets to a named GitHub release. The release APK from this workflow is the Skip-generated Android artifact; the legacy Gradle APK remains the Kotlin Compose fallback.

Plain Swift package checks run with `SKIP_ZERO=1` so normal Swift compilation is independent from Skip installation. The manual Skip lane installs Skip, runs `skip checkup`, resolves package dependencies, and builds/tests the Skip-enabled package.

Visual coverage is declared in `ci/visual-goldens/hermex-screens.json`. The fast CI tier is split into two workflows:

- `Skip Android Visual APK Build` builds one reusable runtime-fixture Skip APK. It runs on relevant `master` pushes and can also be dispatched manually.
- `Android Visual Screens` captures one smoke screenshot. When the visual APK build succeeds on `master`, this workflow automatically downloads that exact run's `hermex-skip-visual-apk` artifact and captures `session-list` in dark mode on `compact-phone`.

The full visual tier is `Android Visual Fixture Matrix`. It boots one emulator, installs one APK, and captures every selected fixture in one emulator session. For release-candidate work, run `Skip Android Visual APK Build` once, then pass that run id into `Android Visual Fixture Matrix` with `apk_run_id` so the matrix downloads the APK instead of rebuilding it.

GitHub-hosted macOS remains the default. A self-hosted Mac can be enabled without changing workflow files by setting repository variables to JSON strings:

- `HERMEX_MACOS_SKIP_RUNNER`: defaults to `"macos-26"` for Skip APK builds.
- `HERMEX_MACOS_VISUAL_RUNNER`: defaults to `"macos-26-intel"` for emulator screenshot jobs.

For example, a self-hosted runner with labels `self-hosted`, `macOS`, and `hermex-visual` should use:

```text
["self-hosted","macOS","hermex-visual"]
```

The visual workflows also have an opt-in prewarmed Android path for self-hosted runners:

- `HERMEX_ANDROID_SDK_ROOT`: optional Android SDK root override. Defaults to `~/Library/Android/sdk`.
- `HERMEX_VISUAL_PREINSTALLED_ANDROID=1`: skip GitHub cache restore for Android SDK/system images.
- `HERMEX_VISUAL_EMULATOR_AVD`: optional AVD name. Defaults to `hermex-visual`.
- `HERMEX_VISUAL_EMULATOR_SNAPSHOT=1`: allow emulator snapshot boot instead of forcing `-no-snapshot`.
- `HERMEX_VISUAL_EMULATOR_WIPE_DATA=0`: do not pass `-wipe-data` on emulator launch.
- `HERMEX_VISUAL_REUSE_CONNECTED_EMULATOR=1`: skip AVD creation and emulator launch entirely, then use the already-connected `adb` device.

For the fastest self-hosted loop, keep a known-good emulator running on the Mac runner and set:

```text
HERMEX_VISUAL_PREINSTALLED_ANDROID=1
HERMEX_VISUAL_REUSE_CONNECTED_EMULATOR=1
```

If the emulator should be launched by the workflow but boot from a prepared snapshot, set:

```text
HERMEX_VISUAL_PREINSTALLED_ANDROID=1
HERMEX_VISUAL_EMULATOR_SNAPSHOT=1
HERMEX_VISUAL_EMULATOR_WIPE_DATA=0
```

The manual `Android Visual Screens` workflow can still build a Skip APK for one named shared SwiftUI fixture when no `apk_run_id` is supplied. It installs the app in an emulator, pins the Android display size/state from the manifest, and uploads the captured screenshot in the same `<device>/<state>/<screen>.png` layout consumed by the comparer. The local capture command expects an already-running emulator:

```bash
bash ci/capture_skip_android_fixture.sh \
  --screen session-list \
  --state dark \
  --device-name compact-phone \
  --output-root artifacts/android-skip
```

Compare complete iOS and Android screenshot inventories with:

```bash
python3 ci/visual-goldens/compare_screenshots.py \
  --ios-root artifacts/ios-goldens \
  --android-root artifacts/android-skip
```

Endpoint paths across iOS, Android, and `HermexCore` are checked with:

```bash
python3 ci/endpoint_parity.py
```

Shared networking and store behavior is covered by `HermexCoreTests`: request headers, query-bearing routes, JSON bodies, multipart upload/transcribe requests, tolerant transcribe error decoding, 401 mapping, onboarding login state, session refresh, transcript open, chat start, SSE event reduction, stream cancel routing, composer config, workspace, git, and panel loading. `HermexPlatformTests` covers share intake, cache hydration/persistence, voice transcription, speech output, status notification handoff, and stream-event bridging. The Windows workspace cannot run `swift build` or `swift test`; those run in the macOS lane of the GitHub workflow.

Whole-app migration coverage is tracked with:

```bash
python3 ci/skip_migration_audit.py
```

That audit also requires every `HermexUI` feature area in `ci/skip_migration_manifest.json` to name at least one shared SwiftUI entrypoint file.

Skip package wiring is tracked with:

```bash
python3 ci/skip_package_audit.py
```

Runnable Skip Android artifact generation is tracked with:

```bash
bash ci/build_skip_android_app.sh dist/skip-android
```

This command must run on macOS with Skip, Xcode, and the Android SDK installed. It intentionally generates the conventional Skip app shell in a temporary directory so the repository can keep the existing lowercase `android/` fallback project on Windows without conflicting with Skip's generated uppercase `Android/` directory.

Shared SwiftUI parity guardrails are tracked with:

```bash
python3 ci/shared_ui_parity_audit.py
```

## Release Gate

Do not publish an Android release from this branch until the macOS Skip build and visual golden comparison pass. The manual `Android Named Release` workflow requires the input `confirm_visual_parity=visual-parity-passed`, then rebuilds the shared Swift package, the Skip-enabled package, and Kotlin fallback Android artifacts before creating the named GitHub release. Use `Skip Android Named Release` when the release must contain the runnable Skip-exported APK/AAB.
