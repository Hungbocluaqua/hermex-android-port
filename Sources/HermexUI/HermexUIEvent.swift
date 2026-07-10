import Foundation
import HermexCore

public enum HermexUIEvent: Equatable, Sendable {
    case openRoute(HermexRoute)
    case openSession(String)
    case newChat
    case refresh
    case updateOnboardingServerURL(String)
    case updateOnboardingDisplayName(String)
    case updateOnboardingPassword(String)
    case updateOnboardingCustomHeaders(String)
    case testOnboardingConnection
    case testOnboardingConnectionDraft(serverURLString: String, displayName: String, password: String, customHeaderText: String)
    case connectOnboarding
    case connectOnboardingDraft(serverURLString: String, displayName: String, password: String, customHeaderText: String)
    case selectServer(HermexServerIdentity)
    case searchSessions(String)
    case selectProject(String?)
    case projectCommand(HermexProjectCommand)
    case toggleArchived
    case updateDraft(String)
    case sendDraft
    case cancelStream
    case attach
    case attachPhotos
    case removeAttachment(String)
    case startVoice
    case stopVoice
    case selectModel
    case chooseModel(HermexModelOption)
    case selectWorkspace
    case chooseWorkspace(HermexWorkspaceRootDTO)
    case selectProfile
    case chooseProfile(HermexProfileOption)
    case selectReasoning
    case chooseReasoningEffort(String)
    case undo
    case retry
    case compress
    case clearConversation
    case copyMessage(HermexMessageActionContext)
    case listenMessage(HermexMessageActionContext)
    case editMessage(HermexMessageActionContext, String)
    case regenerateMessage(HermexMessageActionContext)
    case forkMessage(HermexMessageActionContext)
    case approval(String)
    case clarify(String)
    case openWorkspaceEntry(HermexWorkspaceEntryDTO)
    case openFile(String)
    case openExternalURL(String)
    case gitAction(String)
    case gitCommand(HermexGitCommand)
    case updateGitCommitMessage(String)
    case selectPanel(HermexPanel)
    case taskCommand(HermexTaskCommand)
    case beginTaskCreation
    case beginTaskEdit(jobID: String)
    case updateTaskDraft(HermexTaskDraft)
    case cancelTaskEditor
    case requestTaskDeletion(jobID: String)
    case cancelTaskDeletion
    case confirmTaskDeletion
    case dismissTaskDetails
    case toggleSkill(name: String, enabled: Bool)
    case openSkillDetail(name: String)
    case loadSkillFile(fileName: String)
    case dismissSkillDetail
    case dismissSkillFile
    case writeMemory(section: String, content: String)
    case selectInsightsRange(days: Int)
    case signOut
}

public extension HermexUIEvent {
    var appAction: HermexAppAction? {
        switch self {
        case .openRoute(let route):
            return .openRoute(route)
        case .openSession(let sessionID):
            return .openSession(sessionID)
        case .newChat:
            return .newChat
        case .refresh:
            return .refresh
        case .updateOnboardingServerURL(let value):
            return .updateOnboardingServerURL(value)
        case .updateOnboardingDisplayName(let value):
            return .updateOnboardingDisplayName(value)
        case .updateOnboardingPassword(let value):
            return .updateOnboardingPassword(value)
        case .updateOnboardingCustomHeaders(let value):
            return .updateOnboardingCustomHeaders(value)
        case .testOnboardingConnection:
            return .testOnboardingConnection
        case .testOnboardingConnectionDraft(let serverURLString, let displayName, let password, let customHeaderText):
            return .testOnboardingConnectionDraft(
                serverURLString: serverURLString,
                displayName: displayName,
                password: password,
                customHeaderText: customHeaderText
            )
        case .connectOnboarding:
            return .connectOnboarding
        case .connectOnboardingDraft(let serverURLString, let displayName, let password, let customHeaderText):
            return .connectOnboardingDraft(
                serverURLString: serverURLString,
                displayName: displayName,
                password: password,
                customHeaderText: customHeaderText
            )
        case .selectServer(let server):
            return .selectServer(server)
        case .selectProject(let projectID):
            return .selectProject(projectID)
        case .projectCommand(let command):
            return .projectCommand(command)
        case .searchSessions(let query):
            return .searchSessions(query)
        case .toggleArchived:
            return .toggleArchived
        case .updateDraft(let draft):
            return .updateDraft(draft)
        case .sendDraft:
            return .sendDraft
        case .cancelStream:
            return .cancelStream
        case .removeAttachment(let id):
            return .removeAttachment(id)
        case .chooseModel(let model):
            return .selectModel(model)
        case .chooseWorkspace(let workspace):
            return .selectWorkspace(workspace)
        case .chooseProfile(let profile):
            return .selectProfile(profile)
        case .chooseReasoningEffort(let effort):
            return .selectReasoningEffort(effort)
        case .undo:
            return .undo
        case .retry:
            return .retry
        case .compress:
            return .compress
        case .clearConversation:
            return .clearConversation
        case .editMessage(let context, let text):
            return .editMessage(context, text)
        case .regenerateMessage(let context):
            return .regenerateMessage(context)
        case .forkMessage(let context):
            return .forkMessage(context)
        case .copyMessage, .listenMessage:
            return nil
        case .approval(let choice):
            return .approval(choice)
        case .clarify(let response):
            return .clarify(response)
        case .openWorkspaceEntry(let entry):
            return .openWorkspaceEntry(entry)
        case .openFile(let path):
            return .openFile(path)
        case .openExternalURL:
            return nil
        case .gitAction(let action):
            return .gitAction(action)
        case .gitCommand(let command):
            return .gitCommand(command)
        case .updateGitCommitMessage(let message):
            return .updateGitCommitMessage(message)
        case .selectPanel(let panel):
            return .selectPanel(panel)
        case .taskCommand(let command):
            return .taskCommand(command)
        case .beginTaskCreation:
            return .beginTaskCreation
        case .beginTaskEdit(let jobID):
            return .beginTaskEdit(jobID: jobID)
        case .updateTaskDraft(let draft):
            return .updateTaskDraft(draft)
        case .cancelTaskEditor:
            return .cancelTaskEditor
        case .requestTaskDeletion(let jobID):
            return .requestTaskDeletion(jobID: jobID)
        case .cancelTaskDeletion:
            return .cancelTaskDeletion
        case .confirmTaskDeletion:
            return .confirmTaskDeletion
        case .dismissTaskDetails:
            return .dismissTaskDetails
        case .toggleSkill(let name, let enabled):
            return .toggleSkill(name: name, enabled: enabled)
        case .openSkillDetail(let name):
            return .openSkillDetail(name: name)
        case .loadSkillFile(let fileName):
            return .loadSkillFile(fileName: fileName)
        case .dismissSkillDetail:
            return .dismissSkillDetail
        case .dismissSkillFile:
            return .dismissSkillFile
        case .writeMemory(let section, let content):
            return .writeMemory(section: section, content: content)
        case .selectInsightsRange(let days):
            return .selectInsightsRange(days: days)
        case .signOut:
            return .signOut
        case .attach,
             .attachPhotos,
             .startVoice,
             .stopVoice,
             .selectModel,
             .selectWorkspace,
             .selectProfile,
             .selectReasoning:
            return nil
        }
    }
}
