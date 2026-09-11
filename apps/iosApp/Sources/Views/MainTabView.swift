import SwiftUI
import SharedKit

struct MainTabView: View {
    let container: AppContainer
    @ObservedObject var authViewModel: AuthViewModel
    @ObservedObject var activeProfileStore: ActiveProfileStore

    var body: some View {
        TabView {
            HomeView(
                viewModel: HomeViewModel(
                    contentRepository: container.contentRepository,
                    playbackRepository: container.playbackRepository,
                    activeProfileStore: activeProfileStore
                ),
                container: container,
                activeProfileStore: activeProfileStore,
                authViewModel: authViewModel
            )
            .tabItem { Label("Home", systemImage: "house.fill") }

            BrowseView(
                viewModel: BrowseViewModel(contentRepository: container.contentRepository),
                container: container,
                activeProfileStore: activeProfileStore,
                authViewModel: authViewModel
            )
            .tabItem { Label("Browse", systemImage: "square.grid.2x2") }

            SearchView(
                viewModel: SearchViewModel(searchRepository: container.searchRepository),
                container: container,
                activeProfileStore: activeProfileStore,
                authViewModel: authViewModel
            )
            .tabItem { Label("Search", systemImage: "magnifyingglass") }

            WatchlistView(
                viewModel: WatchlistViewModel(watchlistRepository: container.watchlistRepository),
                container: container,
                activeProfileStore: activeProfileStore,
                authViewModel: authViewModel
            )
            .tabItem { Label("My List", systemImage: "bookmark.fill") }
        }
        .tint(.brand)
    }
}

extension View {
    func signOutToolbar(authViewModel: AuthViewModel) -> some View {
        toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button("Sign out") { authViewModel.logout() }
                    .foregroundColor(.brand)
            }
        }
    }
}
