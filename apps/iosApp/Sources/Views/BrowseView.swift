import SwiftUI
import SharedKit

struct BrowseView: View {
    @StateObject var viewModel: BrowseViewModel
    let container: AppContainer
    let activeProfileStore: ActiveProfileStore
    @ObservedObject var authViewModel: AuthViewModel
    @State private var path: [String] = []

    private let types: [ContentType?] = [nil, .movie, .series]

    var body: some View {
        NavigationStack(path: $path) {
            ZStack {
                Color.appBackground.ignoresSafeArea()
                VStack(spacing: 0) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(Array(types.enumerated()), id: \.offset) { _, type in
                                let selected = viewModel.selectedType == type
                                Button { viewModel.selectType(type) } label: {
                                    Text(label(for: type))
                                        .padding(.horizontal, 16).padding(.vertical, 8)
                                        .background(selected ? Color.brand : Color.appSurface)
                                        .foregroundColor(.white)
                                        .clipShape(Capsule())
                                }
                            }
                        }
                        .padding(.horizontal, 16).padding(.vertical, 12)
                    }

                    if viewModel.isLoading {
                        LoadingView()
                    } else if let error = viewModel.error {
                        ErrorView(message: error)
                    } else {
                        ScrollView {
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 120))], spacing: 12) {
                                ForEach(viewModel.items, id: \.id) { item in
                                    Button { path.append(item.id) } label: { ContentCardView(content: item) }
                                        .buttonStyle(.plain)
                                        .onAppear {
                                            if item.id == viewModel.items.last?.id { viewModel.loadMore() }
                                        }
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                }
            }
            .navigationTitle("Browse")
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

    private func label(for type: ContentType?) -> String {
        guard let type else { return "All" }
        return type.name
    }
}
