#!/usr/bin/env python3
"""Release-only readiness checks for the Skip Android artifact.

The static parity workflow is intentionally allowed to pass while the port is
in progress. Release/upload workflows are stricter: they must not publish a
Skip APK that can install but cannot authenticate, fetch sessions, or satisfy
the visual-golden acceptance gate.
"""

from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def fail(message: str) -> bool:
    print(message)
    return False


def main() -> int:
    api_client = read("Sources/HermexCore/HermexAPIClient.swift")
    launcher = read("ci/skip-hermex-app/HermexSkipApp.swift")
    prepare_script = read("ci/prepare_skip_hermex_app.py")
    export_script = read("ci/build_skip_android_app.sh")
    workflow = read(".github/workflows/skip-android-named-release.yml")
    visual_workflow = read(".github/workflows/visual-golden-compare.yml")
    android_visual_workflow = read(".github/workflows/android-visual-screens.yml")
    android_matrix_workflow = read(".github/workflows/android-visual-fixture-matrix.yml")
    manifest = read("ci/visual-goldens/hermex-screens.json")

    ok = True
    for marker in [
        "networkingUnavailable",
        "Live networking is not enabled in this Skip Android build yet.",
        "Hermex Skip Android networking transport is not connected.",
        "Hermex Skip Android preview transport is not connected.",
        "HermexSessionsResponse(sessions: [], projects: [])",
    ]:
        ok &= marker not in api_client or fail(
            f"Skip Android release is blocked by non-live networking marker: {marker}"
        )

    ok &= "confirm_visual_parity" in workflow or fail(
        "Skip Android release workflow must require visual parity confirmation."
    )
    ok &= "confirm_live_networking" in workflow or fail(
        "Skip Android release workflow must require live networking confirmation."
    )
    ok &= "visual-parity-passed" in workflow or fail(
        "Skip Android release workflow must name the visual parity passphrase."
    )
    ok &= "visual_golden_run_id" in workflow or fail(
        "Skip Android release workflow must require a successful Visual Golden Compare run ID."
    )
    ok &= "Visual Golden Compare" in workflow or fail(
        "Skip Android release workflow must verify the Visual Golden Compare workflow name."
    )
    ok &= "gh run view" in workflow or fail(
        "Skip Android release workflow must inspect the visual compare run result."
    )
    ok &= "live-networking-passed" in workflow or fail(
        "Skip Android release workflow must name the live networking passphrase."
    )
    ok &= "#if HERMEX_ENABLE_DEMO_STORE && !SKIP" in read("Sources/HermexCore/HermexAppStore.swift") or fail(
        "Skip Android release must compile the live HermexAppStore path."
    )
    ok &= "syncFromStore()" in read("Sources/HermexUI/HermexStoreRootScreen.swift") and "@State private var appState" in read("Sources/HermexUI/HermexStoreRootScreen.swift") or fail(
        "Skip Android release must mirror store snapshots into @State for UI redraws."
    )
    ok &= "hermexRuntimeVisualFixturesEnabled = false" in launcher and "if hermexRuntimeVisualFixturesEnabled" in launcher or fail(
        "Skip Android release launcher must ignore persisted runtime visual fixture selector files by default."
    )
    ok &= "--enable-runtime-visual-fixtures" in prepare_script and "HERMEX_ENABLE_RUNTIME_VISUAL_FIXTURES" in prepare_script or fail(
        "Skip Android app preparation must require explicit opt-in for runtime fixture selector files."
    )
    ok &= "--enable-runtime-visual-fixtures" in export_script and "HERMEX_ENABLE_RUNTIME_VISUAL_FIXTURES" in export_script or fail(
        "Skip Android app export must not enable runtime fixture selector files unless visual CI requests it."
    )
    ok &= "chat-keyboard-open" in manifest and "panels-insights" in manifest or fail(
        "Visual-golden manifest must cover keyboard/chat and panel parity screens."
    )
    ok &= "validate_screenshot_inventory.py" in visual_workflow or fail(
        "Visual Golden Compare must validate complete screenshot inventories."
    )
    ok &= "compare_screenshots.py" in visual_workflow or fail(
        "Visual Golden Compare must run the pixel diff."
    )
    ok &= "ci/capture_skip_android_fixture.sh" in android_visual_workflow or fail(
        "Android Visual Screens must capture Skip fixture screenshots from an emulator."
    )
    ok &= "actions/upload-artifact" in android_visual_workflow or fail(
        "Android Visual Screens must upload screenshot artifacts for visual comparison."
    )
    ok &= "ci/capture_skip_android_fixture_matrix.sh" in android_matrix_workflow or fail(
        "Android Visual Fixture Matrix must capture multiple screens from one reusable APK."
    )
    ok &= "Start Android emulator in background" in android_matrix_workflow or fail(
        "Android Visual Fixture Matrix must overlap emulator startup with APK build work."
    )

    if ok:
        print("Skip release readiness audit OK")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
