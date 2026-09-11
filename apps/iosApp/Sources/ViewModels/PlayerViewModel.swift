import Foundation
import SharedKit

enum PlayerUiState {
    case loading
    case error(String)
    case ready(PlaybackGrant)
}

@MainActor
final class PlayerViewModel: ObservableObject {
    @Published private(set) var uiState: PlayerUiState = .loading

    private let contentId: String
    private let playbackRepository: PlaybackRepository

    init(contentId: String, playbackRepository: PlaybackRepository, activeProfileStore: ActiveProfileStore) {
        self.contentId = contentId
        self.playbackRepository = playbackRepository
        guard let profileId = activeProfileStore.activeProfileId else {
            uiState = .error("No profile selected.")
            return
        }
        Task {
            do {
                let command = StartPlaybackCommand(contentId: contentId, profileId: profileId)
                let grant = try await ThrowingWrappersKt.startOrThrow(playbackRepository, command: command, userId: "")
                uiState = .ready(grant)
            } catch {
                uiState = .error(readableMessage(error))
            }
        }
    }

    func reportPosition(positionSeconds: Int64, durationSeconds: Int64?, completed: Bool = false) {
        guard case .ready(let grant) = uiState else { return }
        Task {
            let boxedDuration = durationSeconds.map { KotlinLong(value: $0) }
            let command = UpdatePositionCommand(positionSeconds: positionSeconds, durationSeconds: boxedDuration, completed: completed)
            _ = try? await ThrowingWrappersKt.updatePositionOrThrow(playbackRepository, sessionId: grant.session.id, userId: "", command: command)
        }
    }
}
