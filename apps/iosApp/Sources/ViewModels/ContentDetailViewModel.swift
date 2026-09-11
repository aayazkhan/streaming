import Foundation
import SharedKit

enum ContentDetailUiState {
    case loading
    case error(String)
    case loaded(detail: ContentDetail, isInWatchlist: Bool, isUpdatingWatchlist: Bool)
}

@MainActor
final class ContentDetailViewModel: ObservableObject {
    @Published private(set) var uiState: ContentDetailUiState = .loading

    private let contentId: String
    private let contentRepository: ContentRepository
    private let watchlistRepository: WatchlistRepository

    init(contentId: String, contentRepository: ContentRepository, watchlistRepository: WatchlistRepository) {
        self.contentId = contentId
        self.contentRepository = contentRepository
        self.watchlistRepository = watchlistRepository
        load()
    }

    private func load() {
        Task {
            uiState = .loading
            do {
                let detail = try await ThrowingWrappersKt.findByIdOrThrow(contentRepository, contentId: contentId)
                let watchlist = try? await ThrowingWrappersKt.listOrThrow(watchlistRepository, userId: "", limit: 50, cursor: nil)
                let items = (watchlist?.items as? [WatchlistEntry]) ?? []
                let isInList = items.contains { $0.content.id == contentId }
                uiState = .loaded(detail: detail, isInWatchlist: isInList, isUpdatingWatchlist: false)
            } catch {
                uiState = .error(readableMessage(error))
            }
        }
    }

    func toggleWatchlist() {
        guard case .loaded(let detail, let isInWatchlist, _) = uiState else { return }
        uiState = .loaded(detail: detail, isInWatchlist: isInWatchlist, isUpdatingWatchlist: true)
        Task {
            do {
                if isInWatchlist {
                    try await ThrowingWrappersKt.removeOrThrow(watchlistRepository, userId: "", contentId: contentId)
                } else {
                    _ = try await ThrowingWrappersKt.addOrThrow(watchlistRepository, userId: "", contentId: contentId)
                }
                uiState = .loaded(detail: detail, isInWatchlist: !isInWatchlist, isUpdatingWatchlist: false)
            } catch {
                uiState = .loaded(detail: detail, isInWatchlist: isInWatchlist, isUpdatingWatchlist: false)
            }
        }
    }
}
