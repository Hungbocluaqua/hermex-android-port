import SwiftUI
import HermexCore

public struct HermexVisualFixtureRootScreen: View {
    private let fixture: HermexVisualFixture

    public init(fixture: HermexVisualFixture) {
        self.fixture = fixture
    }

    public init?(screenName: String) {
        guard let fixture = HermexVisualFixtureCatalog.fixture(named: screenName) else {
            return nil
        }
        self.fixture = fixture
    }

    public var body: some View {
        HermexRootScreen(
            appState: fixture.appState,
            onboarding: fixture.onboarding,
            sessions: fixture.sessions,
            chat: fixture.chat,
            settings: fixture.settings,
            workspace: fixture.workspace,
            git: fixture.git,
            panels: fixture.panels,
            onEvent: { _ in }
        )
        .accessibilityIdentifier("hermex-visual-fixture-\(fixture.screenName)")
    }
}
