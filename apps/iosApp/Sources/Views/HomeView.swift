import SwiftUI
import SharedKit

struct HomeView: View {
    @StateObject var viewModel: HomeViewModel
    let container: AppContainer
    let activeProfileStore: ActiveProfileStore
    @ObservedObject var authViewModel: AuthViewModel
    @State private var path: [String] = []

    var body: some View {
        NavigationStack(path: $path) {
            ZStack {
                Color.appBackground.ignoresSafeArea()
                switch viewModel.uiState {
                case .loading:
                    LoadingView()
                case .error(let message):
                    ErrorView(message: message)
                case .loaded(let feed, let continueWatching):
                    ScrollView {
                        VStack(alignment: .leading, spacing: 4) {
                            if !continueWatching.isEmpty {
                                ContentRowView(
                                    title: "Continue Watching",
                                    items: continueWatching.map { $0.content },
                                    onSelect: { path.append($0.id) }
                                )
                            }
                            ForEach(feed.sections, id: \.key) { section in
                                ContentRowView(title: section.title, items: section.items as! [ContentSummary], onSelect: { path.append($0.id) })
                            }
                        }
                        .padding(.vertical, 8)
                    }
                }
            }
            .navigationTitle("Streaming")
            .navigationDestination(for: String.self) { contentId in
                ContentDetailView(
                    viewModel: ContentDetailViewModel(contentId: contentId, contentRepository: container.contentRepository, watchlistRepository: container.watchlistRepository),
                    container: container,
                    activeProfileStore: activeProfileStore
                )
            }
            .signOutToolbar(authViewModel: authViewModel)
        }
    }
}
