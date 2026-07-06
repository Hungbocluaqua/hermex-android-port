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
    workflow = read(".github/workflows/skip-android-named-release.yml")
    visual_workflow = read(".github/workflows/visual-golden-compare.yml")
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
    ok &= "chat-keyboard-open" in manifest and "panels-insights" in manifest or fail(
        "Visual-golden manifest must cover keyboard/chat and panel parity screens."
    )
    ok &= "validate_screenshot_inventory.py" in visual_workflow or fail(
        "Visual Golden Compare must validate complete screenshot inventories."
    )
    ok &= "compare_screenshots.py" in visual_workflow or fail(
        "Visual Golden Compare must run the pixel diff."
    )

    if ok:
        print("Skip release readiness audit OK")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
