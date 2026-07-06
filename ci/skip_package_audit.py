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
    ok &= require((ROOT / "ci" / "prepare_skip_hermex_app.py").is_file(), "Skip Android app preparation script is missing.")
    ok &= require((ROOT / "ci" / "skip_release_readiness_audit.py").is_file(), "Skip Android release readiness audit is missing.")
    ok &= require((ROOT / "ci" / "visual-goldens" / "validate_screenshot_inventory.py").is_file(), "Screenshot inventory validator is missing.")
    ok &= require((ROOT / "ci" / "visual-goldens" / "validate_fixture_catalog.py").is_file(), "Visual fixture catalog validator is missing.")
    ok &= require((ROOT / "ci" / "skip-hermex-app" / "HermexSkipApp.swift").is_file(), "Skip Android app Swift launcher template is missing.")
    ok &= require((ROOT / ".github" / "workflows" / "skip-android-named-release.yml").is_file(), "Skip Android release workflow is missing.")
    ok &= require((ROOT / ".github" / "workflows" / "android-visual-screens.yml").is_file(), "Android visual screenshot workflow is missing.")
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
    ok &= require("HermexVisualFixtureCatalog.fixture(named:" in launcher, "Skip launcher must resolve visual fixtures by golden screen name.")
    ok &= require("HermexVisualFixtureRootScreen" in launcher, "Skip launcher must be able to render shared visual fixtures.")
    ok &= require("HermexAppEnvironment" in launcher, "Skip launcher must provide a live HermexAppEnvironment.")
    ok &= require("HermexAPIClient" in launcher, "Skip launcher must connect shared UI to HermexAPIClient.")

    prepare_script = (ROOT / "ci" / "prepare_skip_hermex_app.py").read_text(encoding="utf-8")
    ok &= require("--visual-fixture-name" in prepare_script, "Skip app preparation must accept a named visual fixture.")
    ok &= require("HERMEX_VISUAL_FIXTURE_NAME" in prepare_script, "Skip app preparation must read the visual fixture env var.")
    ok &= require("hermex-screens.json" in prepare_script, "Skip app preparation must validate fixture names against the golden inventory.")
    ok &= require("hermexVisualFixtureName" in prepare_script, "Skip app preparation must inject the selected visual fixture into the launcher.")

    export_script = (ROOT / "ci" / "build_skip_android_app.sh").read_text(encoding="utf-8")
    ok &= require("skip init --transpiled-app" in export_script, "Skip app export must generate a transpiled app shell.")
    ok &= require("swift build --target" in export_script, "Skip app export must run the Skip-enabled Swift build.")
    ok &= require("assembleDebug" in export_script, "Skip app export must build the generated Android Gradle project.")
    ok &= require("HERMEX_VISUAL_FIXTURE_NAME" in export_script and "--visual-fixture-name" in export_script, "Skip app export must forward named fixture builds for visual diff capture.")
    ok &= require("skip_release_readiness_audit.py" in export_script, "Skip app export must run release readiness checks by default.")
    ok &= require("HERMEX_ALLOW_INCOMPLETE_SKIP_APK" in export_script, "Skip app export must require an explicit debug override for incomplete APKs.")
    ok &= require("hermes_mobile_dark_icon.png" in export_script, "Skip app export must use the real Hermex launcher icon asset.")
    ok &= require("hermex_app_icon.png" in export_script, "Skip app export must copy a Hermex launcher PNG into generated Android resources.")
    ok &= require('android:label="Hermex"' in export_script, "Skip app export must force the generated launcher label to Hermex.")
    ok &= require("android_app_name" in export_script, "Skip app export must rewrite generated Android app-name strings.")

    capture_script = (ROOT / "ci" / "capture_skip_android_fixture.sh").read_text(encoding="utf-8")
    ok &= require("HERMEX_VISUAL_FIXTURE_NAME" in capture_script, "Android visual capture must build a named shared fixture.")
    ok &= require("ci/visual-goldens/hermex-screens.json" in capture_script, "Android visual capture must validate against the golden manifest.")
    ok &= require("cmd uimode night" in capture_script, "Android visual capture must set light/dark system appearance.")
    ok &= require("wm size" in capture_script and "wm density" in capture_script, "Android visual capture must pin emulator display dimensions.")
    ok &= require("screencap -p" in capture_script, "Android visual capture must collect a real emulator screenshot.")
    ok &= require("chat-keyboard-open" in capture_script and "input tap" in capture_script, "Android visual capture must attempt keyboard-open fixture focus.")

    store = (ROOT / "Sources" / "HermexCore" / "HermexAppStore.swift").read_text(encoding="utf-8")
    ok &= require("isFreshInstallOnboarding" in store, "HermexAppStore must keep preview sessions out of fresh onboarding.")
    ok &= require("shouldSeedPreviewData" in store, "HermexAppStore demo data must be gated behind non-fresh-run state.")

    fixtures = (ROOT / "Sources" / "HermexCore" / "HermexVisualFixtures.swift").read_text(encoding="utf-8")
    fixture_root = (ROOT / "Sources" / "HermexUI" / "HermexVisualFixtureRootScreen.swift").read_text(encoding="utf-8")
    ok &= require("HermexVisualFixtureCatalog" in fixtures, "HermexCore must expose a typed visual fixture catalog.")
    ok &= require("chat-keyboard-open" in fixtures and "prefersKeyboardVisible = true" in fixtures, "Visual fixtures must include keyboard-open chat state.")
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
    ok &= require("emulator" in android_visual_workflow and "adb devices" in android_visual_workflow, "Android visual workflow must boot an emulator and wait for adb registration.")
    ok &= require("yes | sdkmanager --licenses" in android_visual_workflow, "Android visual workflow must accept SDK licenses before installing system images.")
    ok &= require("yes | sdkmanager --install \"$system_image\"" in android_visual_workflow, "Android visual workflow must install emulator images non-interactively.")
    ok &= require("for attempt in 1 2 3" in android_visual_workflow and "install_skip()" in android_visual_workflow, "Android visual workflow must retry flaky Skip installs.")
    ok &= require("adb start-server" in android_visual_workflow and "pgrep -f \"emulator.*hermex-visual\"" in android_visual_workflow, "Android visual workflow must bound emulator boot waits and dump logs on failure.")
    ok &= require("-no-snapshot" in android_visual_workflow and "-wipe-data" in android_visual_workflow, "Android visual workflow must start a deterministic fresh emulator.")
    ok &= require("actions/upload-artifact" in android_visual_workflow, "Android visual workflow must upload screenshot artifacts.")

    parity_workflow = (ROOT / ".github" / "workflows" / "skip-android-parity.yml").read_text(encoding="utf-8")
    ok &= require("Skip Generated APK Smoke" in parity_workflow, "Skip parity workflow must expose a generated APK smoke job.")
    ok &= require("HERMEX_ALLOW_INCOMPLETE_SKIP_APK" in parity_workflow, "Skip APK smoke job must explicitly mark generated artifacts incomplete.")
    ok &= require("actions/upload-artifact" in parity_workflow, "Skip APK smoke job must upload generated artifacts for inspection.")
    ok &= require("capture_skip_android_fixture.sh --self-test" in parity_workflow, "Skip parity workflow must self-test Android visual capture wiring.")
    ok &= require("for attempt in 1 2 3" in parity_workflow and "install_skip()" in parity_workflow, "Skip APK smoke job must retry flaky Skip installs.")

    if ok:
        print("Skip package audit OK")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
