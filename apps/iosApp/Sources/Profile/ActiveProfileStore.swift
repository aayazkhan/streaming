import Foundation

/// Mirrors androidApp's ActiveProfileHolder — plain (non-secret) UserDefaults, app-level UI state
/// rather than business logic, so unlike the token store this doesn't need to be shared Kotlin.
@MainActor
final class ActiveProfileStore: ObservableObject {
    @Published var activeProfileId: String? {
        didSet { persist() }
    }

    init() {
        activeProfileId = UserDefaults.standard.string(forKey: Self.key)
    }

    func clear() {
        activeProfileId = nil
    }

    private func persist() {
        if let activeProfileId {
            UserDefaults.standard.set(activeProfileId, forKey: Self.key)
        } else {
            UserDefaults.standard.removeObject(forKey: Self.key)
        }
    }

    private static let key = "streaming_active_profile"
}
