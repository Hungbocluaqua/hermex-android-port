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
    fixture_root = read("Sources/HermexUI/HermexVisualFixtureRootScreen.swift")
    onboarding = read("Sources/HermexUI/HermexOnboardingScreen.swift")
    session_list = read("Sources/HermexUI/HermexSessionListScreen.swift")
    chat = read("Sources/HermexUI/HermexChatScreen.swift")
    workspace = read("Sources/HermexUI/HermexWorkspaceScreen.swift")
    git = read("Sources/HermexUI/HermexGitScreen.swift")
    panels = read("Sources/HermexUI/HermexPanelsScreen.swift")
    settings = read("Sources/HermexUI/HermexSettingsScreen.swift")
    chrome = read("Sources/HermexUI/HermexSharedChrome.swift")
    events = read("Sources/HermexUI/HermexUIEvent.swift")
    store = read("Sources/HermexCore/HermexAppStore.swift")
    mapping = read("Sources/HermexCore/HermexStateMapping.swift")
    package = read("Package.swift")

    ok = True
    ok &= require("NavigationStack" not in root, "HermexRootScreen must not add platform navigation chrome.")
    ok &= require("HermexOnboardingScreen(appState: appState, onboarding: onboarding" in root, "Root screen must pass shared onboarding state.")
    store_root = read("Sources/HermexUI/HermexStoreRootScreen.swift")
    ok &= require("syncFromStore()" in store_root and "@State private var appState" in store_root, "Store root must mirror store snapshots into @State for Skip redraws.")
    ok &= require("HermexVisualFixtureRootScreen" in fixture_root, "HermexUI must expose a renderable visual fixture root.")
    ok &= require("HermexVisualFixtureCatalog.fixture(named:" in fixture_root, "Visual fixture root must resolve named golden screens.")
    ok &= require("HermexRootScreen(" in fixture_root, "Visual fixture root must render through the canonical HermexRootScreen.")
    for state in ["fixture.appState", "fixture.onboarding", "fixture.sessions", "fixture.chat", "fixture.settings", "fixture.workspace", "fixture.git", "fixture.panels"]:
        ok &= require(state in fixture_root, f"Visual fixture root must pass {state} into HermexRootScreen.")
    ok &= require("TextField(" in onboarding and "SecureField(" in onboarding, "Onboarding must expose server/password inputs.")
    ok &= require("HermexAppIconMark" in onboarding and "HermesAppIcon" in chrome, "Onboarding must render the shared Hermex app icon.")
    ok &= require(
        'text: $serverURLString' in onboarding and 'text: $password' in onboarding and "testOnboardingConnectionDraft" in onboarding,
        "Onboarding fields must use direct editable state bindings and submit typed drafts on Android."
    )
    ok &= require(
        "submitConnection()" in onboarding and ".onSubmit" in onboarding,
        "Onboarding keyboard submit must route through the same connection action."
    )
    ok &= require(
        "@FocusState private var focusedField" in onboarding and "keyboardActionBar" in onboarding and "hermexOnboardingKeyboardInset" in onboarding,
        "Onboarding must preserve the iOS focused-field keyboard action bar."
    )
    ok &= require(
        'onboardingField(systemImage: "link"' in onboarding
        and 'onboardingField(systemImage: "key.fill"' in onboarding
        and "HermexSystemImageName(systemImage)" in onboarding,
        "Onboarding field icons must use Skip-safe HermexSystemImageName mapping."
    )
    ok &= require(
        "connectionButtonLabel(title: title, systemImage: systemImage)" in onboarding
        and "#if SKIP\n        Text(title)" in onboarding,
        "Onboarding connection buttons must avoid unsupported Skip system images."
    )
    ok &= require(
        "onEvent(.updateOnboardingServerURL(newValue))" in onboarding and "onEvent(.updateOnboardingPassword(newValue))" in onboarding,
        "Onboarding text changes must mirror into shared store state so Skip Android does not lose typed input."
    )
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
    ok &= require("HERMEX_SKIP_PREVIEW_STORE" not in store, "Skip release builds must not compile the old preview-store path.")
    ok &= require("Self.server(from: appState.auth)" in store, "Preview store must derive active server without changing first-run auth.")
    ok &= require("HermexLogoMark()" in session_list, "Session list must render the shared HERMEX logo.")
    ok &= require("utilityRail" not in session_list, "Session list must not use the pre-parity side rail layout.")
    ok &= require("LazyVStack(alignment: .leading, spacing: 0)" in session_list, "Session list must use the iOS row rhythm.")
    ok &= require("sessionListTopChromeBottomPadding" in session_list, "Session list must preserve iOS top chrome row spacing.")
    ok &= require("utilityRows" in session_list and "sidebarNavigationRow" in session_list, "Session list must expose iOS-style full-width utility rows.")
    ok &= require("sessionListBottomSpacerHeight" in session_list, "Session list must reserve the iOS bottom spacer behind the floating chat button.")
    ok &= require("sessionListRowActionSize" in session_list, "Session rows must preserve the trailing action button contract.")
    ok &= require("searchChrome" in session_list and "searchChromeIsExpanded" in session_list, "Session list must expose iOS-style expandable search chrome.")
    ok &= require("HermexSystemImageName(\"square.and.pencil\")" in session_list, "Skip Android must not render a warning placeholder for the floating Chat icon.")
    ok &= require("func HermexSystemImageName" in chrome, "Shared chrome must expose Skip-safe system image mapping.")
    ok &= require("activeServerSection" in settings and "serverManagementSection" in settings, "Settings screen must expose iOS-style active server and server management cards.")
    ok &= require("serverCard" in settings and ".selectServer(server)" in settings, "Settings screen must allow switching saved servers.")
    ok &= require("Add Server" in settings and ".openRoute(.onboarding)" in settings, "Settings screen must expose add-server onboarding affordance.")
    ok &= require("Privacy & Security" in settings and "Passwords" in settings and "Not stored" in settings, "Settings screen must state password storage behavior.")
    ok &= require("Sign Out" in settings and ".signOut" in settings, "Settings screen must expose sign-out action.")
    ok &= require("Chat Defaults" in settings and "Default Model" in settings and "Default Profile" in settings, "Settings screen must expose chat default settings.")
    ok &= require("HermexIconCluster" in chat, "Chat top chrome icon cluster is missing.")
    ok &= require("chatHeader" in chat, "Chat screen must use custom iOS-style top chrome.")
    ok &= require("TextField(\"Message Hermex\"" not in chat, "Composer must not regress to the pre-parity single-line TextField.")
    ok &= require("TextEditor(text: draftBinding)" in chat, "Composer text input must use an iOS-style multi-line editor.")
    ok &= require("@State private var localDraft" in chat and "onEvent(.updateDraft(newValue))" in chat, "Composer must keep a local draft binding so Skip text entry remains interactive.")
    ok &= require("Ask anything... /commands" in chat, "Composer placeholder must match the iOS composer.")
    ok &= require("composerTextInputHeight" in chat, "Composer must preserve the iOS measured/capped input height contract.")
    ok &= require("composerTextInputMaximumHeight" in chat, "Composer must cap text growth like the iOS composer.")
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
    ok &= require("rootScroller" in workspace and "directoryCard" in workspace, "Workspace screen must expose iOS-style roots and directory card surfaces.")
    ok &= require("openWorkspaceEntry" in workspace and "previewPanel" in workspace, "Workspace screen must support directory/file preview navigation.")
    ok &= require("currentDirectoryTitle" in workspace and "formattedSize" in workspace, "Workspace screen must preserve readable path and file metadata chrome.")
    ok &= require("HermexIconCluster" in workspace and "Parent Folder" in workspace, "Workspace top chrome must expose iOS-style grouped navigation actions.")
    ok &= require("doc.badge.gearshape" in workspace and "Binary file" in workspace, "Workspace preview must account for binary files.")
    ok &= require("loadDirectory" in store and "loadFile" in store, "Shared store is missing workspace loaders.")
    ok &= require("fromDirectoryResponse" in mapping and "fromJSON(_ value" in mapping, "Workspace JSON mappers are missing.")
    ok &= require("repositoryCard" in git and "gitCommand" in git, "Git screen must expose repository status and typed actions.")
    ok &= require("remoteActionBar" in git and "metricPill" in git, "Git screen must expose iOS-style repository metrics and remote action chips.")
    ok &= require("commitBox" in git and "updateGitCommitMessage" in git, "Git screen must expose commit message controls.")
    ok &= require("stagedSummary" in git and "Commit message" in git, "Git commit box must expose staged summary and editable message.")
    ok &= require("gitFileRow" in git and "fileAction" in git, "Git changes list must expose iOS-style per-file action rows.")
    ok &= require("Unified diff" in git and "diffPanel" in git, "Git screen must expose a styled unified diff panel.")
    for command in [".diff(", ".stage(", ".unstage(", ".discard(", ".commit("]:
        ok &= require(command in git, f"Git screen is missing command {command}.")
    ok &= require("loadGitStatus" in store and "performGitCommand" in store, "Shared store is missing typed git loaders/actions.")
    ok &= require("case .diff" in store and "case .stage" in store and "case .commit" in store, "Shared store is missing typed git command handling.")
    ok &= require("fromStatusResponse" in mapping, "Git status mapper is missing.")
    ok &= require("diffText(from" in mapping and "mergingStatus" in mapping, "Git diff/status merge helpers are missing.")
    ok &= require("Server Panels" in panels and "selectedPanelCard" in panels, "Panels screen must use the iOS-style Server Panels card shell.")
    ok &= require("panelPicker" not in panels, "Panels screen must not regress to the placeholder segmented picker.")
    ok &= require("taskRows" in panels and "skillRows" in panels and "memoryRows" in panels and "insightsPanel" in panels, "Panels screen must expose tasks/skills/memory/insights sections.")
    ok &= require("Search skills..." in panels and "filteredSkills" in panels, "Skills panel must expose the iOS-style search field.")
    ok &= require("New Task" in panels and "Details" in panels and "Run" in panels, "Tasks panel must expose iOS-style task actions.")
    ok &= require("Edit \\(section.section)" in panels, "Memory panel must expose iOS-style edit action chrome.")
    ok &= require("Last 30 Days" in panels and "Estimated Cost" in panels and "Cache Hit Rate" in panels, "Insights panel must expose iOS-style analytics rows.")
    ok &= require(".foregroundStyle(HermexUIColors.primaryText)" in panels, "Panels screen must explicitly style dark-surface primary text.")
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
