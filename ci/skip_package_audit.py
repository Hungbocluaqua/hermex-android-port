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
    ok &= require((ROOT / "ci" / "prepare_skip_hermex_app.py").is_file(), "Skip Android app preparation script is missing.")
    ok &= require((ROOT / "ci" / "skip_release_readiness_audit.py").is_file(), "Skip Android release readiness audit is missing.")
    ok &= require((ROOT / "ci" / "skip-hermex-app" / "HermexSkipApp.swift").is_file(), "Skip Android app Swift launcher template is missing.")
    ok &= require((ROOT / ".github" / "workflows" / "skip-android-named-release.yml").is_file(), "Skip Android release workflow is missing.")
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
    ok &= require("HermexAppEnvironment" in launcher, "Skip launcher must provide a live HermexAppEnvironment.")
    ok &= require("HermexAPIClient" in launcher, "Skip launcher must connect shared UI to HermexAPIClient.")

    export_script = (ROOT / "ci" / "build_skip_android_app.sh").read_text(encoding="utf-8")
    ok &= require("skip init --transpiled-app" in export_script, "Skip app export must generate a transpiled app shell.")
    ok &= require("swift build --target" in export_script, "Skip app export must run the Skip-enabled Swift build.")
    ok &= require("assembleDebug" in export_script, "Skip app export must build the generated Android Gradle project.")
    ok &= require("skip_release_readiness_audit.py" in export_script, "Skip app export must run release readiness checks by default.")
    ok &= require("HERMEX_ALLOW_INCOMPLETE_SKIP_APK" in export_script, "Skip app export must require an explicit debug override for incomplete APKs.")
    ok &= require("hermes_mobile_dark_icon.png" in export_script, "Skip app export must use the real Hermex launcher icon asset.")
    ok &= require("hermex_app_icon.png" in export_script, "Skip app export must copy a Hermex launcher PNG into generated Android resources.")
    ok &= require('android:label="Hermex"' in export_script, "Skip app export must force the generated launcher label to Hermex.")
    ok &= require("android_app_name" in export_script, "Skip app export must rewrite generated Android app-name strings.")

    store = (ROOT / "Sources" / "HermexCore" / "HermexAppStore.swift").read_text(encoding="utf-8")
    ok &= require("isFreshInstallOnboarding" in store, "HermexAppStore must keep preview sessions out of fresh onboarding.")
    ok &= require("shouldSeedPreviewData" in store, "HermexAppStore demo data must be gated behind non-fresh-run state.")

    onboarding_screen = (ROOT / "Sources" / "HermexUI" / "HermexOnboardingScreen.swift").read_text(encoding="utf-8")
    ok &= require(
        "testOnboardingConnectionDraft" in onboarding_screen and "connectOnboardingDraft" in onboarding_screen,
        "HermexOnboardingScreen must submit the locally typed connection draft on Test/Connect.",
    )

    workflow = (ROOT / ".github" / "workflows" / "skip-android-named-release.yml").read_text(encoding="utf-8")
    ok &= require("ci/build_skip_android_app.sh" in workflow, "Skip Android release workflow must call the app export script.")
    ok &= require("gh release create" in workflow, "Skip Android release workflow must publish a GitHub release.")
    ok &= require("confirm_visual_parity" in workflow and "visual-parity-passed" in workflow, "Skip Android release workflow must require visual parity confirmation.")
    ok &= require("confirm_live_networking" in workflow and "live-networking-passed" in workflow, "Skip Android release workflow must require live networking confirmation.")
    ok &= require("ci/skip_release_readiness_audit.py" in workflow, "Skip Android release workflow must run release readiness checks.")

    parity_workflow = (ROOT / ".github" / "workflows" / "skip-android-parity.yml").read_text(encoding="utf-8")
    ok &= require("Skip Generated APK Smoke" in parity_workflow, "Skip parity workflow must expose a generated APK smoke job.")
    ok &= require("HERMEX_ALLOW_INCOMPLETE_SKIP_APK" in parity_workflow, "Skip APK smoke job must explicitly mark generated artifacts incomplete.")
    ok &= require("actions/upload-artifact" in parity_workflow, "Skip APK smoke job must upload generated artifacts for inspection.")

    if ok:
        print("Skip package audit OK")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
