import SwiftUI

@main
struct StreamingApp: App {
    private let container: AppContainer
    @StateObject private var authViewModel: AuthViewModel
    @StateObject private var activeProfileStore: ActiveProfileStore

    init() {
        let container = AppContainer()
        self.container = container
        _authViewModel = StateObject(wrappedValue: AuthViewModel(sessionManager: container.sessionManager))
        _activeProfileStore = StateObject(wrappedValue: ActiveProfileStore())
    }

    var body: some Scene {
        WindowGroup {
            RootView(container: container, authViewModel: authViewModel, activeProfileStore: activeProfileStore)
        }
    }
}
