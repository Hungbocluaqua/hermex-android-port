#!/usr/bin/env python3
"""Audit the local Swift package is wired for a Skip-first Android path."""

from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT / "Package.swift"


def require(ok: bool, message: str) -> bool:
    if not ok:
        print(message)
    return ok


def main() -> int:
    text = PACKAGE.read_text(encoding="utf-8")
    ok = True

    ok &= require("// swift-tools-version: 6.1" in text, "Package.swift must use Swift tools 6.1 for current Skip.")
    ok &= require("https://source.skip.tools/skip.git" in text, "Package.swift must depend on Skip.")
    ok &= require("https://source.skip.tools/skip-ui.git" in text, "Package.swift must depend on SkipUI.")
    ok &= require('.plugin(name: "skipstone", package: "skip")' in text, "Skipstone plugin must be attached to shared targets.")
    ok &= require("SKIP_ZERO" in text, "Package.swift must support SKIP_ZERO for plain Swift CI.")
    ok &= require((ROOT / "Sources" / "HermexCore" / "Skip" / "skip.yml").is_file(), "HermexCore Skip config is missing.")
    ok &= require((ROOT / "Sources" / "HermexPlatform" / "Skip" / "skip.yml").is_file(), "HermexPlatform Skip config is missing.")
    ok &= require((ROOT / "Sources" / "HermexUI" / "Skip" / "skip.yml").is_file(), "HermexUI Skip config is missing.")
    ok &= require((ROOT / "ci" / "build_skip_android_app.sh").is_file(), "Skip Android app export script is missing.")
    ok &= require((ROOT / "ci" / "capture_skip_android_fixture.sh").is_file(), "Android visual fixture capture script is missing.")
    ok &= require((ROOT / "ci" / "capture_skip_android_fixture_matrix.sh").is_file(), "Android visual fixture matrix capture script is missing.")
    pixel_guard = ROOT / "ci" / "assert_android_capture_not_system_dialog.py"
    ok &= require(pixel_guard.is_file(), "Android system-dialog screenshot guard is missing.")
    ok &= require((ROOT / "ci" / "prepare_skip_hermex_app.py").is_file(), "Skip Android app preparation script is missing.")
    ok &= require((ROOT / "ci" / "skip_release_readiness_audit.py").is_file(), "Skip Android release readiness audit is missing.")
    ok &= require((ROOT / "ci" / "visual-goldens" / "validate_screenshot_inventory.py").is_file(), "Screenshot inventory validator is missing.")
    ok &= require((ROOT / "ci" / "visual-goldens" / "validate_fixture_catalog.py").is_file(), "Visual fixture catalog validator is missing.")
    ok &= require((ROOT / "ci" / "skip-hermex-app" / "HermexSkipApp.swift").is_file(), "Skip Android app Swift launcher template is missing.")
    ok &= require((ROOT / ".github" / "workflows" / "skip-android-named-release.yml").is_file(), "Skip Android release workflow is missing.")
    ok &= require((ROOT / ".github" / "workflows" / "android-visual-screens.yml").is_file(), "Android visual screenshot workflow is missing.")
    ok &= require((ROOT / ".github" / "workflows" / "android-visual-fixture-matrix.yml").is_file(), "Android visual fixture matrix workflow is missing.")
    ok &= require((ROOT / ".github" / "workflows" / "visual-golden-compare.yml").is_file(), "Visual Golden Compare workflow is missing.")
    ok &= require('resources: [.process("Resources")]' in text, "HermexUI must process shared resources.")

    for relative in [
        "Sources/HermexCore/Skip/skip.yml",
        "Sources/HermexPlatform/Skip/skip.yml",
        "Sources/HermexUI/Skip/skip.yml",
    ]:
        content = (ROOT / relative).read_text(encoding="utf-8").strip()
        ok &= require(content == "mode: transpiled", f"{relative} must set `mode: transpiled`.")

    for asset in [
        "hermes-fill-mask.png",
        "hermes-shading-overlay.png",
        "hermes-highlight.png",
        "hermes-outline-shadow.png",
    ]:
        ok &= require(
            (ROOT / "Sources" / "HermexUI" / "Resources" / "Logo" / asset).is_file(),
            f"Shared Hermex logo asset is missing: {asset}.",
        )

    launcher = (ROOT / "ci" / "skip-hermex-app" / "HermexSkipApp.swift").read_text(encoding="utf-8")
    ok &= require("HermexStoreRootScreen" in launcher, "Skip launcher must render HermexStoreRootScreen.")
    ok &= require("hermexVisualFixtureName" in launcher, "Skip launcher must expose a visual fixture injection point.")
    ok &= require(
        "hermexRuntimeVisualFixturesEnabled = false" in launcher
        and "if hermexRuntimeVisualFixturesEnabled" in launcher,
        "Skip launcher must ignore persisted runtime visual fixture files unless visual CI explicitly enables them.",
    )
    ok &= require("resolvedHermexVisualFixtureName" in launcher, "Skip launcher must resolve visual fixture selection at runtime.")
    ok &= require("hermex_visual_fixture.txt" in launcher, "Skip launcher must read the runtime visual fixture selector file.")
    ok &= require("HermexVisualFixtureCatalog.fixture(named:" in launcher, "Skip launcher must resolve visual fixtures by golden screen name.")
    ok &= require("HermexVisualFixtureRootScreen" in launcher, "Skip launcher must be able to render shared visual fixtures.")
    ok &= require("HermexAppEnvironment" in launcher, "Skip launcher must provide a live HermexAppEnvironment.")
    ok &= require("HermexAPIClient" in launcher, "Skip launcher must connect shared UI to HermexAPIClient.")
    ok &= require(
        "import HermexPlatform" in launcher
        and "HermexSecureDataStore" in launcher
        and "HermexSkipPersistence" in launcher
        and "HermexSkipCookieTransport" in launcher
        and "HermexSkipCacheStore" in launcher,
        "Skip launcher must provide secure durable server, cookie, and offline-cache services."
    )
    ok &= require("bootstrap()" in launcher and "restoredStoreState" in launcher, "Skip launcher must restore persisted server state at startup.")
    ok &= require("updateServerRuntime" in launcher and "activateServer" in launcher, "Skip launcher must rebind the runtime when the active server changes.")

    prepare_script = (ROOT / "ci" / "prepare_skip_hermex_app.py").read_text(encoding="utf-8")
    ok &= require("--visual-fixture-name" in prepare_script, "Skip app preparation must accept a named visual fixture.")
    ok &= require("HERMEX_VISUAL_FIXTURE_NAME" in prepare_script, "Skip app preparation must read the visual fixture env var.")
    ok &= require("hermex-screens.json" in prepare_script, "Skip app preparation must validate fixture names against the golden inventory.")
    ok &= require("hermexVisualFixtureName" in prepare_script, "Skip app preparation must inject the selected visual fixture into the launcher.")
    ok &= require(
        "--enable-runtime-visual-fixtures" in prepare_script
        and "HERMEX_ENABLE_RUNTIME_VISUAL_FIXTURES" in prepare_script,
        "Skip app preparation must require an explicit opt-in before runtime fixture selector files are honored.",
    )
    ok &= require(
        '.product(name: "HermexPlatform", package: "HermexShared")' in prepare_script,
        "Skip app preparation must link HermexPlatform for durable Android services.",
    )

    package_manifest = (ROOT / "Package.swift").read_text(encoding="utf-8")
    platform_services = (ROOT / "Sources" / "HermexPlatform" / "HermexPlatformServices.swift").read_text(encoding="utf-8")
    ok &= require(
        "skip-keychain.git" in package_manifest
        and '.product(name: "SkipKeychain", package: "skip-keychain")' in package_manifest
        and "import SkipKeychain" in platform_services
        and "HermexSecureDataStore" in platform_services,
        "HermexPlatform must use SkipKeychain for encrypted Android persistence.",
    )

    export_script = (ROOT / "ci" / "build_skip_android_app.sh").read_text(encoding="utf-8")
    ok &= require("skip init --transpiled-app" in export_script, "Skip app export must generate a transpiled app shell.")
    ok &= require("swift build --target" in export_script, "Skip app export must run the Skip-enabled Swift build.")
    ok &= require("assembleDebug" in export_script, "Skip app export must build the generated Android Gradle project.")
    ok &= require(
        "patch_android_release_rules" in export_script
        and "com.google.errorprone.annotations.Immutable" in export_script,
        "Skip release builds must suppress the compile-time Tink annotation missing-class failure in R8.",
    )
    ok &= require("HERMEX_VISUAL_FIXTURE_NAME" in export_script and "--visual-fixture-name" in export_script, "Skip app export must forward named fixture builds for visual diff capture.")
    ok &= require(
        "HERMEX_ENABLE_RUNTIME_VISUAL_FIXTURES" in export_script
        and "--enable-runtime-visual-fixtures" in export_script,
        "Skip app export must forward runtime fixture opt-in only when visual CI requests it.",
    )
    ok &= require("skip_release_readiness_audit.py" in export_script, "Skip app export must run release readiness checks by default.")
    ok &= require("HERMEX_ALLOW_INCOMPLETE_SKIP_APK" in export_script, "Skip app export must require an explicit debug override for incomplete APKs.")
    ok &= require(
        "HERMEX_REUSE_SKIP_APP_WORKDIR" in export_script and "Reusing generated Skip app workspace" in export_script,
        "Skip app export must support reusing a valid generated workspace for cached CI builds.",
    )
    ok &= require("hermes_mobile_dark_icon.png" in export_script, "Skip app export must use the real Hermex launcher icon asset.")
    ok &= require("hermex_app_icon.png" in export_script, "Skip app export must copy a Hermex launcher PNG into generated Android resources.")
    ok &= require('android:label="Hermex"' in export_script, "Skip app export must force the generated launcher label to Hermex.")
    ok &= require("android_app_name" in export_script, "Skip app export must rewrite generated Android app-name strings.")
    ok &= require(
        "tools.skip.SkipKeychain.xml" in export_script
        and "hermex_data_extraction_rules.xml" in export_script,
        "Skip app export must exclude encrypted SkipKeychain storage from Android backups.",
    )

    capture_script = (ROOT / "ci" / "capture_skip_android_fixture.sh").read_text(encoding="utf-8")
    ok &= require("HERMEX_VISUAL_FIXTURE_NAME" in capture_script, "Android visual capture must build a named shared fixture.")
    ok &= require("ci/visual-goldens/hermex-screens.json" in capture_script, "Android visual capture must validate against the golden manifest.")
    ok &= require("cmd uimode night" in capture_script, "Android visual capture must set light/dark system appearance.")
    ok &= require("wm size" in capture_script and "wm density" in capture_script, "Android visual capture must pin emulator display dimensions.")
    ok &= require("screencap -p" in capture_script, "Android visual capture must collect a real emulator screenshot.")
    ok &= require("chat-keyboard-open" in capture_script and "input tap" in capture_script, "Android visual capture must attempt keyboard-open fixture focus.")
    ok &= require(
        "HERMEX_VISUAL_KEYBOARD_SETTLE_SECONDS" in capture_script
        and "KEYBOARD_SETTLE_SECONDS" in capture_script,
        "Android visual capture must give the chat keyboard fixture an extra Skip cold-start settle window.",
    )
    ok &= require(
        "composer_draft_input_center" in capture_script
        and "hermex-composer-draft-input" in capture_script,
        "Android visual capture must tap the actual composer input instead of a fixed keyboard coordinate.",
    )
    ok &= require(
        "Reopening Android soft keyboard before screenshot attempt" in capture_script
        and "request_android_keyboard_for_fixture || true" in capture_script,
        "Android visual capture must reopen the chat keyboard immediately before screenshot retries.",
    )
    ok &= require(
        "mIsImeShowing=true" in capture_script
        and "Window\\{.*InputMethod" not in capture_script,
        "Android visual capture must not treat a hidden InputMethod window as a visible keyboard.",
    )
    ok &= require("resolve_launch_activity" in capture_script and "am start -W" in capture_script, "Android visual capture must launch Hermex directly, not through a flaky launcher surface.")
    ok &= require("--reuse-apk" in capture_script and "REUSE_APK" in capture_script, "Android visual capture must support reusing a prebuilt fixture APK.")
    ok &= require("wait_for_app_focus" in capture_script and "pidof \"$PACKAGE_ID\"" in capture_script, "Android visual capture must wait for the Hermex process before screenshots.")
    ok &= require("adb_shell_bounded" in capture_script and "dumpsys window" in capture_script, "Android visual capture diagnostics must use bounded adb shell probes.")
    ok &= require("logcat -d -t 2000" in capture_script and "/data/anr" in capture_script, "Android visual capture diagnostics must include logcat and ANR traces.")
    ok &= require("Hermex process was not running at screenshot time" in capture_script, "Android visual capture must reject screenshots when Hermex is not running.")
    ok &= require("Screenshot was blocked by a system/ANR dialog" in capture_script, "Android visual capture must reject screenshots of system dialogs.")
    ok &= require("assert_android_capture_not_system_dialog.py" in capture_script, "Android visual capture must inspect the actual PNG before upload.")
    pixel_guard_script = pixel_guard.read_text(encoding="utf-8")
    ok &= require(
        "keyboard_required_and_visible" in pixel_guard_script
        and "not keyboard_required_and_visible" in pixel_guard_script
        and "36 <= max_channel <= 248" in pixel_guard_script,
        "Android screenshot guard must allow light soft-keyboard captures without treating them as system dialogs.",
    )
    ok &= require("focused_window_snapshot" in capture_script and "is_blocking_system_window" in capture_script, "Android visual capture must reject blocking system/ANR dialogs from focus snapshots.")
    ok &= require("CLOSE_SYSTEM_DIALOGS" in capture_script, "Android visual capture must ask Android to close transient system dialogs before launch retries.")
    ok &= require(
        "Application Not Responding|ANR in|isn.t responding" in capture_script
        and "quiet_background_system_apps" in capture_script,
        "Android visual capture must dismiss and reject hosted-runner system/launcher ANRs before retrying Hermex launch.",
    )
    ok &= require("write_visual_fixture_selection" in capture_script and "hermex_visual_fixture.txt" in capture_script, "Android visual capture must select fixture screens at runtime.")
    ok &= require("HERMEX_ENABLE_RUNTIME_VISUAL_FIXTURES=1" in capture_script, "Android visual capture must opt visual APKs into runtime fixture selection.")
    ok &= require("--skip-install" in capture_script and "SKIP_INSTALL" in capture_script, "Android visual capture must support reusing one installed APK across multiple screens.")

    capture_matrix_script = (ROOT / "ci" / "capture_skip_android_fixture_matrix.sh").read_text(encoding="utf-8")
    ok &= require("capture_skip_android_fixture.sh" in capture_matrix_script, "Android fixture matrix capture must reuse the single-screen capture helper.")
    ok &= require("unset HERMEX_VISUAL_FIXTURE_NAME" in capture_matrix_script, "Android fixture matrix capture must build one reusable runtime-selected APK.")
    ok &= require("--skip-install" in capture_matrix_script, "Android fixture matrix capture must avoid reinstalling after the first screen.")

    store = (ROOT / "Sources" / "HermexCore" / "HermexAppStore.swift").read_text(encoding="utf-8")
    ok &= require("isFreshInstallOnboarding" in store, "HermexAppStore must keep preview sessions out of fresh onboarding.")
    ok &= require("#if HERMEX_ENABLE_DEMO_STORE && !SKIP" in store, "Skip Android must compile the live HermexAppStore, not the demo/preview store.")
    ok &= require("shouldSeedPreviewData" in store, "HermexAppStore demo data must be gated behind non-fresh-run state.")

    fixtures = (ROOT / "Sources" / "HermexCore" / "HermexVisualFixtures.swift").read_text(encoding="utf-8")
    chat_screen = (ROOT / "Sources" / "HermexUI" / "HermexChatScreen.swift").read_text(encoding="utf-8")
    fixture_root = (ROOT / "Sources" / "HermexUI" / "HermexVisualFixtureRootScreen.swift").read_text(encoding="utf-8")
    ok &= require("HermexVisualFixtureCatalog" in fixtures, "HermexCore must expose a typed visual fixture catalog.")
    ok &= require("chat-keyboard-open" in fixtures and "prefersKeyboardVisible = true" in fixtures, "Visual fixtures must include keyboard-open chat state.")
    ok &= require(
        "hermex-composer-draft-input" in chat_screen
        and "requestDraftFocusIfPreferred" in chat_screen,
        "Chat keyboard fixture must expose a stable shared composer input.",
    )
    ok &= require("chat-slash-menu" in fixtures and "overlay = .slashMenu" in fixtures, "Visual fixtures must include slash menu chat state.")
    ok &= require("chat-attachments" in fixtures and "overlay = .attachmentPicker" in fixtures, "Visual fixtures must include attachment composer state.")
    ok &= require("chat-approval" in fixtures and "pendingApproval" in fixtures, "Visual fixtures must include approval prompt state.")
    ok &= require("HermexRootScreen(" in fixture_root and "fixture.chat" in fixture_root, "HermexUI must render visual fixtures through shared screen state.")

    onboarding_screen = (ROOT / "Sources" / "HermexUI" / "HermexOnboardingScreen.swift").read_text(encoding="utf-8")
    ok &= require(
        "testOnboardingConnectionDraft" in onboarding_screen and "connectOnboardingDraft" in onboarding_screen,
        "HermexOnboardingScreen must submit the locally typed connection draft on Test/Connect.",
    )

    workflow = (ROOT / ".github" / "workflows" / "skip-android-named-release.yml").read_text(encoding="utf-8")
    ok &= require("ci/build_skip_android_app.sh" in workflow, "Skip Android release workflow must call the app export script.")
    ok &= require("gh release create" in workflow, "Skip Android release workflow must publish a GitHub release.")
    ok &= require("confirm_visual_parity" in workflow and "visual-parity-passed" in workflow, "Skip Android release workflow must require visual parity confirmation.")
    ok &= require("visual_golden_run_id" in workflow and "gh run view" in workflow, "Skip Android release workflow must verify a successful Visual Golden Compare run.")
    ok &= require("confirm_live_networking" in workflow and "live-networking-passed" in workflow, "Skip Android release workflow must require live networking confirmation.")
    ok &= require("ci/skip_release_readiness_audit.py" in workflow, "Skip Android release workflow must run release readiness checks.")

    visual_workflow = (ROOT / ".github" / "workflows" / "visual-golden-compare.yml").read_text(encoding="utf-8")
    ok &= require("validate_screenshot_inventory.py" in visual_workflow, "Visual Golden Compare must validate screenshot inventory coverage.")
    ok &= require("validate_fixture_catalog.py" in visual_workflow, "Visual Golden Compare must validate shared fixture coverage.")
    ok &= require("compare_screenshots.py" in visual_workflow, "Visual Golden Compare must run the screenshot pixel diff.")

    android_visual_workflow = (ROOT / ".github" / "workflows" / "android-visual-screens.yml").read_text(encoding="utf-8")
    ok &= require("ci/capture_skip_android_fixture.sh" in android_visual_workflow, "Android visual workflow must call the fixture capture script.")
    ok &= require("workflow_dispatch" in android_visual_workflow and "screen:" in android_visual_workflow, "Android visual workflow must expose manual fixture selection.")
    ok &= require("macos-26-intel" in android_visual_workflow, "Android visual workflow must use an Intel macOS runner for emulator acceleration.")
    ok &= require("emulator" in android_visual_workflow and "adb devices" in android_visual_workflow, "Android visual workflow must boot an emulator and wait for adb registration.")
    ok &= require("yes | sdkmanager --licenses" in android_visual_workflow, "Android visual workflow must accept SDK licenses before installing system images.")
    ok &= require(
        "install_system_image()" in android_visual_workflow
        and "yes | sdkmanager --install \"$image\"" in android_visual_workflow,
        "Android visual workflow must install emulator image candidates non-interactively.",
    )
    ok &= require("for attempt in 1 2 3" in android_visual_workflow and "install_skip()" in android_visual_workflow, "Android visual workflow must retry flaky Skip installs.")
    ok &= require("actions/cache@v4" in android_visual_workflow, "Android visual workflow must cache SwiftPM and Android SDK dependencies.")
    ok &= require("gradle/actions/setup-gradle" in android_visual_workflow, "Android visual workflow must enable the Gradle cache.")
    ok &= require(
        "adb start-server" in android_visual_workflow
        and (
            'pgrep -f "emulator.*hermex-visual"' in android_visual_workflow
            or 'pgrep -f "emulator.*$HERMEX_VISUAL_EMULATOR_AVD"' in android_visual_workflow
        ),
        "Android visual workflow must bound emulator boot waits and dump logs on failure.",
    )
    ok &= require("for _ in {1..180}" in android_visual_workflow, "Android visual workflow must allow enough time for cold emulator boots on hosted runners.")
    ok &= require("-no-snapshot" in android_visual_workflow and "-wipe-data" in android_visual_workflow, "Android visual workflow must start a deterministic fresh emulator.")
    ok &= require("Start Android emulator in background" in android_visual_workflow and "Wait for Android emulator boot" in android_visual_workflow, "Android visual workflow must overlap emulator startup with the Skip APK build.")
    ok &= require("Build reusable Skip APK" in android_visual_workflow and "timeout-minutes: 35" in android_visual_workflow, "Android visual workflow must build the reusable APK in a bounded pre-capture step.")
    ok &= require("HERMEX_ENABLE_RUNTIME_VISUAL_FIXTURES=1" in android_visual_workflow, "Android visual workflow must opt reusable visual APKs into runtime fixture selection.")
    ok &= require("--reuse-apk" in android_visual_workflow and "timeout-minutes: 15" in android_visual_workflow, "Android visual screenshot capture must reuse the prebuilt APK and be tightly bounded.")
    ok &= require("actions/upload-artifact" in android_visual_workflow, "Android visual workflow must upload screenshot artifacts.")

    android_matrix_workflow = (ROOT / ".github" / "workflows" / "android-visual-fixture-matrix.yml").read_text(encoding="utf-8")
    ok &= require("capture_skip_android_fixture_matrix.sh" in android_matrix_workflow, "Android visual matrix workflow must use the one-build multi-screen capture script.")
    ok &= require("Start Android emulator in background" in android_matrix_workflow, "Android visual matrix workflow must overlap emulator startup with reusable APK work.")
    ok &= require("actions/cache@v4" in android_matrix_workflow and "gradle/actions/setup-gradle" in android_matrix_workflow, "Android visual matrix workflow must cache dependencies.")
    ok &= require("HERMEX_ENABLE_RUNTIME_VISUAL_FIXTURES=1" in android_matrix_workflow, "Android visual matrix workflow must opt reusable visual APKs into runtime fixture selection.")

    parity_workflow = (ROOT / ".github" / "workflows" / "skip-android-parity.yml").read_text(encoding="utf-8")
    ok &= require("Skip Generated APK Smoke" in parity_workflow, "Skip parity workflow must expose a generated APK smoke job.")
    ok &= require(
        "build_generated_apk" in parity_workflow
        and "type: boolean" in parity_workflow
        and "inputs.build_generated_apk == true" in parity_workflow,
        "Skip parity workflow must make generated APK smoke explicitly opt-in.",
    )
    ok &= require(
        "actions/cache@v4" in parity_workflow
        and "gradle/actions/setup-gradle" in parity_workflow
        and "Sources/**/*.swift" in parity_workflow
        and "steps.skip_generated_cache.outputs.cache-hit" in parity_workflow
        and "HERMEX_REUSE_SKIP_APP_WORKDIR" in parity_workflow,
        "Skip parity workflow must cache SwiftPM/Gradle state and reuse the generated workspace only on an exact source cache hit.",
    )
    ok &= require(
        "Parity Audits" in parity_workflow
        and "run_android" in parity_workflow
        and "run_visual" in parity_workflow,
        "Skip parity workflow must combine audits and scope Android/visual jobs by changed paths.",
    )
    ok &= require("HERMEX_ALLOW_INCOMPLETE_SKIP_APK" in parity_workflow, "Skip APK smoke job must explicitly mark generated artifacts incomplete.")
    ok &= require("actions/upload-artifact" in parity_workflow, "Skip APK smoke job must upload generated artifacts for inspection.")
    ok &= require("capture_skip_android_fixture.sh --self-test" in parity_workflow, "Skip parity workflow must self-test Android visual capture wiring.")
    ok &= require("for attempt in 1 2 3" in parity_workflow and "install_skip()" in parity_workflow, "Skip APK smoke job must retry flaky Skip installs.")

    if ok:
        print("Skip package audit OK")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
