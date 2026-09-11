import SwiftUI

struct RootView: View {
    let container: AppContainer
    @ObservedObject var authViewModel: AuthViewModel
    @ObservedObject var activeProfileStore: ActiveProfileStore

    var body: some View {
        Group {
            switch authViewModel.phase {
            case .initializing:
                LoadingView()
            case .signedOut:
                AuthFlowView(authViewModel: authViewModel)
            case .signedIn:
                if activeProfileStore.activeProfileId == nil {
                    ProfileSelectView(
                        viewModel: ProfileViewModel(profileRepository: container.profileRepository, activeProfileStore: activeProfileStore)
                    )
                } else {
                    MainTabView(container: container, authViewModel: authViewModel, activeProfileStore: activeProfileStore)
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}

private struct AuthFlowView: View {
    @ObservedObject var authViewModel: AuthViewModel
    @State private var showRegister = false

    var body: some View {
        NavigationStack {
            if showRegister {
                RegisterView(authViewModel: authViewModel, onNavigateToLogin: { showRegister = false })
            } else {
                LoginView(authViewModel: authViewModel, onNavigateToRegister: { showRegister = true })
            }
        }
    }
}
