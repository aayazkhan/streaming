import SwiftUI
import SharedKit

struct ContentDetailView: View {
    @StateObject var viewModel: ContentDetailViewModel
    let container: AppContainer
    let activeProfileStore: ActiveProfileStore
    @State private var showPlayer = false

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            switch viewModel.uiState {
            case .loading:
                LoadingView()
            case .error(let message):
                ErrorView(message: message)
            case .loaded(let detail, let isInWatchlist, let isUpdating):
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(detail.summary.title)
                            .font(.largeTitle.bold())
                            .foregroundColor(.white)

                        HStack(spacing: 8) {
                            if let year = detail.summary.releaseYear {
                                Text(String(year.intValue)).foregroundColor(.gray)
                            }
                            if let rating = detail.summary.rating {
                                Text("★ \(String(format: "%.1f", rating.doubleValue))").foregroundColor(.gray)
                            }
                        }

                        if let synopsis = detail.summary.synopsis {
                            Text(synopsis).foregroundColor(.white)
                        }

                        HStack(spacing: 12) {
                            Button {
                                showPlayer = true
                            } label: {
                                Text(detail.playable ? "Play" : "Unavailable")
                                    .padding(.horizontal, 24).padding(.vertical, 12)
                            }
                            .background(RoundedRectangle(cornerRadius: 24).fill(detail.playable ? Color.brand : Color.gray))
                            .foregroundColor(.white)
                            .disabled(!detail.playable)

                            Button {
                                viewModel.toggleWatchlist()
                            } label: {
                                Text(isInWatchlist ? "Remove from My List" : "Add to My List")
                                    .padding(.horizontal, 24).padding(.vertical, 12)
                            }
                            .background(RoundedRectangle(cornerRadius: 24).stroke(Color.brand))
                            .foregroundColor(.brand)
                            .disabled(isUpdating)
                        }
                    }
                    .padding(24)
                }
            }
        }
        .fullScreenCover(isPresented: $showPlayer) {
            if case .loaded(let detail, _, _) = viewModel.uiState {
                PlayerView(
                    viewModel: PlayerViewModel(contentId: detail.summary.id, playbackRepository: container.playbackRepository, activeProfileStore: activeProfileStore)
                )
            }
        }
    }
}
