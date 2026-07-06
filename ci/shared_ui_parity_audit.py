#!/usr/bin/env python3
"""Static guardrails for the shared SwiftUI source used by the Skip Android port."""

from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def require(ok: bool, message: str) -> bool:
    if not ok:
        print(message)
    return ok


def main() -> int:
    root = read("Sources/HermexUI/HermexRootScreen.swift")
    onboarding = read("Sources/HermexUI/HermexOnboardingScreen.swift")
    session_list = read("Sources/HermexUI/HermexSessionListScreen.swift")
    chat = read("Sources/HermexUI/HermexChatScreen.swift")
    workspace = read("Sources/HermexUI/HermexWorkspaceScreen.swift")
    git = read("Sources/HermexUI/HermexGitScreen.swift")
    panels = read("Sources/HermexUI/HermexPanelsScreen.swift")
    chrome = read("Sources/HermexUI/HermexSharedChrome.swift")
    events = read("Sources/HermexUI/HermexUIEvent.swift")
    store = read("Sources/HermexCore/HermexAppStore.swift")
    mapping = read("Sources/HermexCore/HermexStateMapping.swift")
    package = read("Package.swift")

    ok = True
    ok &= require("NavigationStack" not in root, "HermexRootScreen must not add platform navigation chrome.")
    ok &= require("HermexOnboardingScreen(appState: appState, onboarding: onboarding" in root, "Root screen must pass shared onboarding state.")
    ok &= require("TextField(" in onboarding and "SecureField(" in onboarding, "Onboarding must expose server/password inputs.")
    ok &= require("HermexAppIconMark" in onboarding and "HermesAppIcon" in chrome, "Onboarding must render the shared Hermex app icon.")
    ok &= require('text: $serverURLString' in onboarding and 'text: $password' in onboarding, "Onboarding fields must use direct SwiftUI state bindings for editable Android text input.")
    ok &= require("TextEditor(" in onboarding and "Custom headers" in onboarding, "Onboarding must expose custom header entry.")
    ok &= require(".testOnboardingConnection" in onboarding and ".connectOnboarding" in onboarding, "Onboarding must route connection test and login.")
    for action in [
        "updateOnboardingServerURL",
        "updateOnboardingPassword",
        "testOnboardingConnection",
        "connectOnboarding",
        "selectServer",
    ]:
        ok &= require(action in events and action in store, f"Shared onboarding action is missing {action}.")
    ok &= require("testServerConnection" in store and "loginToServer" in store, "Shared store is missing auth/server environment hooks.")
    ok &= require("onboarding.password = \"\"" in store, "Shared store must clear transient onboarding passwords.")
    ok &= require("seededAppState.auth = .loggedIn" not in store, "Shared store must not auto-log in or seed sessions on first launch.")
    ok &= require("Self.server(from: appState.auth)" in store, "Preview store must derive active server without changing first-run auth.")
    ok &= require("HermexLogoMark()" in session_list, "Session list must render the shared HERMEX logo.")
    ok &= require("utilityRail" in session_list, "Session list must expose the iOS utility rail contract.")
    ok &= require("sessionListRowActionSize" in session_list, "Session rows must preserve the trailing action button contract.")
    ok &= require("searchChrome" in session_list and "searchChromeIsExpanded" in session_list, "Session list must expose iOS-style expandable search chrome.")
    ok &= require("HermexIconCluster" in chat, "Chat top chrome icon cluster is missing.")
    ok &= require("chatHeader" in chat, "Chat screen must use custom iOS-style top chrome.")
    ok &= require("attachmentStrip" in chat, "Composer must expose the attachment strip.")
    ok &= require("composerStatusBar" in chat, "Composer must expose voice/status bar chrome.")
    ok &= require("showsTurnActions" in chat, "Composer must expose turn action controls.")
    ok &= require("pendingPromptStack" in chat, "Chat screen must render pending approval/clarification prompts.")
    ok &= require("approvalPrompt" in chat and ".approval(" in chat, "Approval prompt UI/actions are missing.")
    ok &= require("clarificationPrompt" in chat and ".clarify(" in chat, "Clarification prompt UI/actions are missing.")
    ok &= require("applyStreamEvent" in store and "appendAssistantToken" in store, "Shared store must reduce SSE stream events into chat state.")
    ok &= require("liveReasoning" in store and "liveToolActivity" in store, "Shared stream reducer must preserve reasoning/tool status.")
    ok &= require("availableModels" in chat and "chooseModel" in chat, "Composer model menu is missing.")
    ok &= require("availableWorkspaces" in chat and "chooseWorkspace" in chat, "Composer workspace menu is missing.")
    ok &= require("availableProfiles" in chat and "chooseProfile" in chat, "Composer profile menu is missing.")
    ok &= require("supportedReasoningEfforts" in chat and "chooseReasoningEffort" in chat, "Composer reasoning menu is missing.")
    for action in [".undo", ".retry", ".compress"]:
        ok &= require(action in chat and f"return {action}" in events, f"Composer action {action} must route through shared Core.")
    for action in ["selectModel", "selectWorkspace", "selectProfile", "selectReasoningEffort"]:
        ok &= require(f"case {action}" in store or f"case .{action}" in store, f"HermexAppStore action is missing {action}.")
    for closure in ["undoSession", "retrySession", "compressSession"]:
        ok &= require(closure in store, f"HermexAppStore environment is missing {closure}.")
    for closure in ["loadModels", "loadProfiles", "loadWorkspaces", "loadReasoning", "saveReasoningEffort"]:
        ok &= require(closure in store, f"HermexAppStore environment is missing composer config closure {closure}.")
    ok &= require("refreshComposerConfiguration" in read("Sources/HermexUI/HermexStoreRootScreen.swift"), "Store root must hydrate composer configuration.")
    ok &= require("openWorkspaceEntry" in workspace and "previewPanel" in workspace, "Workspace screen must support directory/file preview navigation.")
    ok &= require("loadDirectory" in store and "loadFile" in store, "Shared store is missing workspace loaders.")
    ok &= require("fromDirectoryResponse" in mapping and "fromJSON(_ value" in mapping, "Workspace JSON mappers are missing.")
    ok &= require("repositoryCard" in git and "gitCommand" in git, "Git screen must expose repository status and typed actions.")
    ok &= require("commitBox" in git and "updateGitCommitMessage" in git, "Git screen must expose commit message controls.")
    for command in [".diff(", ".stage(", ".unstage(", ".discard(", ".commit("]:
        ok &= require(command in git, f"Git screen is missing command {command}.")
    ok &= require("loadGitStatus" in store and "performGitCommand" in store, "Shared store is missing typed git loaders/actions.")
    ok &= require("case .diff" in store and "case .stage" in store and "case .commit" in store, "Shared store is missing typed git command handling.")
    ok &= require("fromStatusResponse" in mapping, "Git status mapper is missing.")
    ok &= require("diffText(from" in mapping and "mergingStatus" in mapping, "Git diff/status merge helpers are missing.")
    ok &= require("panelPicker" in panels and "taskRows" in panels and "skillRows" in panels and "memoryRows" in panels, "Panels screen must expose tasks/skills/memory/insights sections.")
    for closure in ["loadTasks", "loadSkills", "loadMemory", "loadInsights"]:
        ok &= require(closure in store, f"Shared store is missing panel loader {closure}.")
    for asset in [
        "hermes-fill-mask",
        "hermes-shading-overlay",
        "hermes-highlight",
        "hermes-outline-shadow",
    ]:
        ok &= require(asset in chrome, f"Shared logo rendering is missing {asset}.")
    ok &= require('resources: [.process("Resources")]' in package, "HermexUI package resources are not processed.")

    if ok:
        print("Shared UI parity audit OK")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
