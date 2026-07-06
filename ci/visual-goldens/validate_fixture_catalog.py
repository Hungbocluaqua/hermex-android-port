#!/usr/bin/env python3
"""Validate that shared Swift visual fixtures cover every screenshot manifest screen."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "ci" / "visual-goldens" / "hermex-screens.json"
FIXTURES = ROOT / "Sources" / "HermexCore" / "HermexVisualFixtures.swift"

EXPECTED_ROUTES = {
    "onboarding-welcome": "onboarding",
    "onboarding-connect": "onboarding",
    "session-list": "sessions",
    "session-list-search": "sessions",
    "chat-empty": "chat",
    "chat-populated": "chat",
    "chat-streaming": "chat",
    "chat-keyboard-open": "chat",
    "chat-slash-menu": "chat",
    "chat-attachments": "chat",
    "chat-approval": "chat",
    "settings": "settings",
    "workspace-files": "workspace",
    "git-status": "git",
    "panels-tasks": "panels",
    "panels-skills": "panels",
    "panels-memory": "panels",
    "panels-insights": "panels",
}


def require(ok: bool, message: str) -> bool:
    if not ok:
        print(message)
    return ok


def screen_names_from_swift(text: str) -> list[str]:
    match = re.search(r"public static let screenNames: \[String\] = \[(?P<body>.*?)\n    \]", text, re.S)
    if not match:
        return []
    return re.findall(r'"([^"]+)"', match.group("body"))


def main() -> int:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    manifest_screens = manifest["screens"]
    text = FIXTURES.read_text(encoding="utf-8")
    fixture_screens = screen_names_from_swift(text)

    ok = True
    ok &= require(fixture_screens == manifest_screens, "HermexVisualFixtureCatalog.screenNames must exactly match hermex-screens.json.")
    ok &= require(set(fixture_screens) == set(EXPECTED_ROUTES), "Fixture route expectation map is stale.")

    for screen_name in manifest_screens:
        ok &= require(f'case "{screen_name}":' in text, f"Missing fixture switch case for {screen_name}.")

    for screen_name, route in EXPECTED_ROUTES.items():
        if route == "panels":
            ok &= require(f'case "{screen_name}":' in text and "panelFixture(" in text, f"{screen_name} must use a panel fixture.")
        elif route == "onboarding":
            ok &= require(f'case "{screen_name}":' in text and "route: .onboarding" in text, f"{screen_name} must use onboarding route state.")
        else:
            ok &= require(f'route: .{route}' in text, f"Missing route .{route} fixture coverage.")

    ok &= require("prefersKeyboardVisible = true" in text, "Keyboard-open fixture must request keyboard visibility.")
    ok &= require("overlay = .slashMenu" in text, "Slash menu fixture must expose the slash menu overlay hint.")
    ok &= require("overlay = .attachmentPicker" in text, "Attachment fixture must expose the attachment picker overlay hint.")
    ok &= require("pendingApproval" in text, "Approval fixture must expose a pending approval prompt.")
    ok &= require("HermexAttachmentDTO(" in text, "Attachment fixture must use typed attachment DTOs.")

    if ok:
        print("Visual fixture catalog OK")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
