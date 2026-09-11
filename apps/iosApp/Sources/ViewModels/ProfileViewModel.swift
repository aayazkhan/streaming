import Foundation
import SharedKit

enum ProfileUiState {
    case loading
    case error(String)
    case loaded([Profile])
}

@MainActor
final class ProfileViewModel: ObservableObject {
    @Published private(set) var uiState: ProfileUiState = .loading
    @Published private(set) var isCreating = false

    private let profileRepository: ProfileRepository
    private let activeProfileStore: ActiveProfileStore

    init(profileRepository: ProfileRepository, activeProfileStore: ActiveProfileStore) {
        self.profileRepository = profileRepository
        self.activeProfileStore = activeProfileStore
        load()
    }

    func load() {
        Task {
            uiState = .loading
            do {
                let profiles = try await ThrowingWrappersKt.listProfilesOrThrow(profileRepository)
                uiState = .loaded(profiles)
            } catch {
                uiState = .error(readableMessage(error))
            }
        }
    }

    func selectProfile(_ profileId: String) {
        activeProfileStore.activeProfileId = profileId
    }

    func createProfile(name: String, onCreated: @escaping (String) -> Void) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        isCreating = true
        Task {
            do {
                let command = CreateProfileCommand(name: trimmed, kind: .standard, language: "en", subtitleLanguage: nil, avatarKey: nil)
                let profile = try await ThrowingWrappersKt.createProfileOrThrow(profileRepository, command: command)
                activeProfileStore.activeProfileId = profile.id
                onCreated(profile.id)
            } catch {
                uiState = .error(readableMessage(error))
            }
            isCreating = false
        }
    }
}
