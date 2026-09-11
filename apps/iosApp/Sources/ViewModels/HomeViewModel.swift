import Foundation
import SharedKit

enum HomeUiState {
    case loading
    case error(String)
    case loaded(feed: HomeFeed, continueWatching: [ContinueWatchingItem])
}

@MainActor
final class HomeViewModel: ObservableObject {
    @Published private(set) var uiState: HomeUiState = .loading

    private let contentRepository: ContentRepository
    private let playbackRepository: PlaybackRepository
    private let activeProfileStore: ActiveProfileStore

    init(contentRepository: ContentRepository, playbackRepository: PlaybackRepository, activeProfileStore: ActiveProfileStore) {
        self.contentRepository = contentRepository
        self.playbackRepository = playbackRepository
        self.activeProfileStore = activeProfileStore
        load()
    }

    func load() {
        Task {
            uiState = .loading
            do {
                let feed = try await ThrowingWrappersKt.homeOrThrow(contentRepository)
                let continueWatching = (try? await ThrowingWrappersKt.continueWatchingOrThrow(
                    playbackRepository,
                    userId: "",
                    profileId: activeProfileStore.activeProfileId,
                    limit: 20
                )) ?? []
                uiState = .loaded(feed: feed, continueWatching: continueWatching)
            } catch {
                uiState = .error(readableMessage(error))
            }
        }
    }
}
