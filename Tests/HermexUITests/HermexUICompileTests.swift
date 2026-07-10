import XCTest
import SwiftUI
import HermexCore
@testable import HermexUI

final class HermexUICompileTests: XCTestCase {
    func testRootCanInstantiateEveryTopLevelRoute() {
        let routes: [HermexRoute] = [
            .onboarding,
            .sessions,
            .chat,
            .settings,
            .workspace,
            .git,
            .panels
        ]

        for route in routes {
            let view = HermexRootScreen(appState: HermexAppState(route: route))
            XCTAssertTrue(String(describing: type(of: view)).contains("HermexRootScreen"))
        }
    }

    func testVisualFixtureRootCanInstantiateEveryGoldenScreen() throws {
        XCTAssertFalse(HermexVisualFixtureCatalog.all.isEmpty)

        for fixture in HermexVisualFixtureCatalog.all {
            let view = HermexVisualFixtureRootScreen(fixture: fixture)
            XCTAssertTrue(String(describing: type(of: view)).contains("HermexVisualFixtureRootScreen"))
            XCTAssertNotNil(HermexVisualFixtureRootScreen(screenName: fixture.screenName))
        }

        XCTAssertNil(HermexVisualFixtureRootScreen(screenName: "missing-screen"))
    }

    @MainActor
    func testStoreRootCanInstantiateWithSharedStore() {
        let store = HermexAppStore(environment: HermexAppEnvironment(
            testServerConnection: { _ in .dictionary(["ok": .bool(true)]) },
            loginToServer: { _, _ in .dictionary(["ok": .bool(true)]) },
            loadSessions: { _, _ in HermexSessionsResponse() },
            loadSession: { _ in HermexSessionResponse() },
            startChat: { _, _, _, _, _, _, _ in .dictionary(["ok": .bool(true)]) },
            cancelStream: { _ in .dictionary(["ok": .bool(true)]) },
            respondApproval: { _, _, _ in .dictionary(["ok": .bool(true)]) },
            respondClarification: { _, _, _ in .dictionary(["ok": .bool(true)]) },
            undoSession: { _ in .dictionary(["ok": .bool(true)]) },
            retrySession: { _ in .dictionary(["ok": .bool(true)]) },
            compressSession: { _, _ in .dictionary(["ok": .bool(true)]) },
            loadModels: { HermexModelsResponse() },
            loadProfiles: { HermexProfilesResponse() },
            loadWorkspaces: { HermexWorkspacesResponse() },
            loadReasoning: { _, _ in HermexReasoningResponse() },
            saveReasoningEffort: { _, _, _ in .dictionary(["ok": .bool(true)]) },
            loadDirectory: { _, _ in .dictionary([:]) },
            loadFile: { _, _ in .dictionary([:]) },
            loadGitStatus: { _ in .dictionary([:]) },
            performGitAction: { _, _ in .dictionary(["ok": .bool(true)]) },
            performGitCommand: { _, _ in .dictionary(["ok": .bool(true)]) },
            loadTasks: { .dictionary([:]) },
            performTaskCommand: { _ in .dictionary(["ok": .bool(true)]) },
            loadSkills: { .dictionary([:]) },
            loadSkillContent: { _, _ in .dictionary([:]) },
            toggleSkill: { _, _ in .dictionary(["ok": .bool(true)]) },
            loadMemory: { .dictionary([:]) },
            writeMemory: { _, _ in .dictionary(["ok": .bool(true)]) },
            loadInsights: { _ in .dictionary([:]) },
            logout: { .dictionary(["ok": .bool(true)]) }
        ))
        let view = HermexStoreRootScreen(store: store)

        XCTAssertTrue(String(describing: type(of: view)).contains("HermexStoreRootScreen"))
    }

    func testSharedLayoutTokensMatchIOSContracts() {
        XCTAssertEqual(HermexLayoutContract.chatToolbarActionSlotSize, 44)
        XCTAssertEqual(HermexLayoutContract.hermexLogoAspectRatio, 643 / 185)
        XCTAssertEqual(HermexLayoutContract.sessionListLogoWidth, 160)
        XCTAssertEqual(HermexLayoutContract.topChromeCircleSize, 58)
        XCTAssertEqual(HermexLayoutContract.topChromeClusterSpacing, 0)
        XCTAssertEqual(HermexLayoutContract.sessionListUtilityIconSlotWidth, 28)
        XCTAssertEqual(HermexLayoutContract.sessionListUtilityIconSize, 21)
        XCTAssertEqual(HermexLayoutContract.sessionListUtilityRowMinimumHeight, 44)
        XCTAssertEqual(HermexLayoutContract.sessionListBottomSpacerHeight, 104)
        XCTAssertEqual(HermexLayoutContract.sessionListSelectorHeight, 68)
        XCTAssertEqual(HermexLayoutContract.sessionListRowActionSize, 54)
        XCTAssertEqual(HermexLayoutContract.chatTopBarHeight, 76)
        XCTAssertEqual(HermexLayoutContract.chatTranscriptMessageSpacing, 10)
        XCTAssertEqual(HermexLayoutContract.composerCornerRadiusCollapsed, 22)
        XCTAssertEqual(HermexLayoutContract.composerCornerRadiusExpanded, 26)
        XCTAssertEqual(HermexLayoutContract.composerTextInputMinimumHeight, 42)
        XCTAssertEqual(HermexLayoutContract.composerTextInputMaximumHeight, 96)
        XCTAssertEqual(HermexLayoutContract.composerTextInputCollapsedContentHeight, 22)
        XCTAssertEqual(HermexLayoutContract.composerTextInputLineHeight, 22)
        XCTAssertEqual(HermexLayoutContract.composerTextInputWrapColumn, 44)
        XCTAssertEqual(HermexLayoutContract.composerActionButtonSize, 30)
        XCTAssertEqual(HermexLayoutContract.composerPlusButtonSize, 28)
        XCTAssertEqual(HermexLayoutContract.composerModelControlMaxWidth, 132)
        XCTAssertEqual(HermexLayoutContract.composerReasoningControlWidth, 104)
        XCTAssertEqual(HermexLayoutContract.composerAttachmentStripHeight, 32)
        XCTAssertEqual(HermexLayoutContract.pendingPromptCornerRadius, 18)
        XCTAssertEqual(HermexLayoutContract.sessionRowMinimumHeight, 46)
        XCTAssertEqual(HermexLayoutContract.sessionRowSupplementalMinimumHeight, 54)
        XCTAssertEqual(HermexLayoutContract.sessionListFloatingButtonShadowRadius, 18)
        XCTAssertEqual(HermexLayoutContract.pressedScale, 0.975)
    }

    func testPlatformHandledUIEventsStayOutOfSharedStoreActions() {
        XCTAssertNil(HermexUIEvent.attach.appAction)
        XCTAssertNil(HermexUIEvent.startVoice.appAction)
        XCTAssertNil(HermexUIEvent.stopVoice.appAction)
        XCTAssertEqual(HermexUIEvent.selectProject("p1").appAction, .selectProject("p1"))
        XCTAssertEqual(
            HermexUIEvent.projectCommand(.moveSession(sessionID: "s1", projectID: "p1")).appAction,
            .projectCommand(.moveSession(sessionID: "s1", projectID: "p1"))
        )
        XCTAssertEqual(
            HermexUIEvent.chooseModel(HermexModelOption(id: "m", provider: "p")).appAction,
            .selectModel(HermexModelOption(id: "m", provider: "p"))
        )
        XCTAssertEqual(
            HermexUIEvent.chooseWorkspace(HermexWorkspaceRootDTO(path: "/repo")).appAction,
            .selectWorkspace(HermexWorkspaceRootDTO(path: "/repo"))
        )
        XCTAssertEqual(
            HermexUIEvent.chooseProfile(HermexProfileOption(name: "default")).appAction,
            .selectProfile(HermexProfileOption(name: "default"))
        )
        XCTAssertEqual(HermexUIEvent.chooseReasoningEffort("high").appAction, .selectReasoningEffort("high"))
        XCTAssertEqual(HermexUIEvent.updateOnboardingServerURL("https://example.test").appAction, .updateOnboardingServerURL("https://example.test"))
        XCTAssertEqual(HermexUIEvent.updateOnboardingPassword("secret").appAction, .updateOnboardingPassword("secret"))
        XCTAssertEqual(HermexUIEvent.testOnboardingConnection.appAction, .testOnboardingConnection)
        XCTAssertEqual(HermexUIEvent.connectOnboarding.appAction, .connectOnboarding)
        XCTAssertEqual(HermexUIEvent.gitCommand(.stage(path: "README.md")).appAction, .gitCommand(.stage(path: "README.md")))
        XCTAssertEqual(HermexUIEvent.updateGitCommitMessage("Update").appAction, .updateGitCommitMessage("Update"))
        XCTAssertEqual(HermexUIEvent.selectInsightsRange(days: 365).appAction, .selectInsightsRange(days: 365))
        XCTAssertEqual(HermexUIEvent.beginTaskCreation.appAction, .beginTaskCreation)
        XCTAssertEqual(HermexUIEvent.beginTaskEdit(jobID: "job-1").appAction, .beginTaskEdit(jobID: "job-1"))
        XCTAssertEqual(HermexUIEvent.dismissTaskDetails.appAction, .dismissTaskDetails)
        XCTAssertEqual(HermexUIEvent.openSkillDetail(name: "review").appAction, .openSkillDetail(name: "review"))
        XCTAssertEqual(HermexUIEvent.loadSkillFile(fileName: "README.md").appAction, .loadSkillFile(fileName: "README.md"))
        XCTAssertEqual(HermexUIEvent.dismissSkillDetail.appAction, .dismissSkillDetail)
        XCTAssertEqual(HermexUIEvent.dismissSkillFile.appAction, .dismissSkillFile)
        XCTAssertEqual(
            HermexUIEvent.openWorkspaceEntry(HermexWorkspaceEntryDTO(name: "README.md", path: "/repo/README.md", isDirectory: false)).appAction,
            .openWorkspaceEntry(HermexWorkspaceEntryDTO(name: "README.md", path: "/repo/README.md", isDirectory: false))
        )
        XCTAssertEqual(HermexUIEvent.undo.appAction, .undo)
        XCTAssertEqual(HermexUIEvent.retry.appAction, .retry)
        XCTAssertEqual(HermexUIEvent.compress.appAction, .compress)
    }
}
