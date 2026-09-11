import Foundation
import SharedKit

enum WatchlistUiState {
    case loading
    case error(String)
    case loaded([WatchlistEntry])
}

@MainActor
final class WatchlistViewModel: ObservableObject {
    @Published private(set) var uiState: WatchlistUiState = .loading

    private let watchlistRepository: WatchlistRepository

    init(watchlistRepository: WatchlistRepository) {
        self.watchlistRepository = watchlistRepository
        load()
    }

    func load() {
        Task {
            uiState = .loading
            do {
                let page = try await ThrowingWrappersKt.listOrThrow(watchlistRepository, userId: "", limit: 50, cursor: nil)
                uiState = .loaded(page.items as! [WatchlistEntry])
            } catch {
                uiState = .error(readableMessage(error))
            }
        }
    }
}
