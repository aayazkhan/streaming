import SwiftUI
import SharedKit

struct SearchView: View {
    @StateObject var viewModel: SearchViewModel
    let container: AppContainer
    let activeProfileStore: ActiveProfileStore
    @ObservedObject var authViewModel: AuthViewModel
    @State private var path: [String] = []

    var body: some View {
        NavigationStack(path: $path) {
            ZStack {
                Color.appBackground.ignoresSafeArea()
                VStack {
                    TextField("", text: $viewModel.query, prompt: Text("Search titles...").foregroundColor(.gray))
                        .textFieldStyle(.plain)
                        .padding()
                        .background(RoundedRectangle(cornerRadius: 8).stroke(Color.brand))
                        .foregroundColor(.white)
                        .padding(16)

                    if viewModel.isLoading {
                        LoadingView()
                    } else if let error = viewModel.error {
                        ErrorView(message: error)
                    } else {
                        ScrollView {
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 120))], spacing: 12) {
                                ForEach(viewModel.results, id: \.id) { item in
                                    Button { path.append(item.id) } label: { ContentCardView(content: item) }
                                        .buttonStyle(.plain)
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                    Spacer()
                }
            }
            .navigationTitle("Search")
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
