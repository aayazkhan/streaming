import Foundation
import SharedKit

enum AuthPhase: Equatable {
    case initializing
    case signedOut
    case signedIn

    static func == (lhs: AuthPhase, rhs: AuthPhase) -> Bool {
        switch (lhs, rhs) {
        case (.initializing, .initializing), (.signedOut, .signedOut), (.signedIn, .signedIn): return true
        default: return false
        }
    }
}

@MainActor
final class AuthViewModel: ObservableObject {
    @Published private(set) var phase: AuthPhase = .initializing
    @Published var formError: String?
    @Published private(set) var isSubmitting = false

    private let sessionManager: SessionManager
    private var watcher: FlowWatcher?

    init(sessionManager: SessionManager) {
        self.sessionManager = sessionManager
        watcher = FlowWatcherKt.watch(flow: sessionManager.state) { [weak self] state in
            Task { @MainActor [weak self] in
                self?.apply(state)
            }
        }
        Task { try? await sessionManager.restoreSession() }
    }

    private func apply(_ state: Any?) {
        switch state {
        case is AuthStateInitializing: phase = .initializing
        case is AuthStateSignedOut: phase = .signedOut
        case is AuthStateSignedIn: phase = .signedIn
        default: break
        }
    }

    func login(email: String, password: String) {
        formError = nil
        isSubmitting = true
        Task {
            do {
                try await sessionManager.loginOrThrow(email: email, password: password)
            } catch {
                formError = loginFormMessage(error)
            }
            isSubmitting = false
        }
    }

    func register(email: String, password: String, displayName: String) {
        formError = nil
        isSubmitting = true
        Task {
            do {
                try await sessionManager.registerOrThrow(email: email, password: password, displayName: displayName)
            } catch {
                formError = loginFormMessage(error)
            }
            isSubmitting = false
        }
    }

    func logout() {
        Task { try? await sessionManager.logout() }
    }

    deinit {
        watcher?.cancel()
    }
}
