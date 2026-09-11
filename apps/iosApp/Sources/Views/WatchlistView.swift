import SwiftUI
import SharedKit

struct WatchlistView: View {
    @StateObject var viewModel: WatchlistViewModel
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
                case .loaded(let entries):
                    if entries.isEmpty {
                        Text("Your list is empty.").foregroundColor(.gray)
                    } else {
                        ScrollView {
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 120))], spacing: 12) {
                                ForEach(entries, id: \.content.id) { entry in
                                    Button { path.append(entry.content.id) } label: { ContentCardView(content: entry.content) }
                                        .buttonStyle(.plain)
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                }
            }
            .navigationTitle("My List")
            .navigationDestination(for: String.self) { contentId in
                ContentDetailView(
                    viewModel: ContentDetailViewModel(contentId: contentId, contentRepository: container.contentRepository, watchlistRepository: container.watchlistRepository),
                    container: container,
                    activeProfileStore: activeProfileStore
                )
            }
            .signOutToolbar(authViewModel: authViewModel)
            .onAppear { viewModel.load() }
        }
    }
}
