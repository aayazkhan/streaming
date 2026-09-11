import Foundation
import SharedKit

/// Turns a thrown error from a SharedKit call into UI-safe copy — never a raw stack trace.
/// AppErrorUnauthorized here means a session ApiClient's own refresh-and-retry couldn't save (the
/// refresh token itself is invalid/expired), not bad credentials — that wording only belongs at the
/// login/register call sites, see `loginFormMessage`.
func readableMessage(_ error: Error) -> String {
    guard let appError = appError(from: error) else {
        return (error as NSError).localizedDescription
    }
    switch appError {
    case is AppErrorUnauthorized:
        return "Your session has expired. Please sign in again."
    case is AppErrorNetwork:
        return "Can't reach the server. Check your connection."
    default:
        return appError.message
    }
}

func loginFormMessage(_ error: Error) -> String {
    if appError(from: error) is AppErrorUnauthorized {
        return "Incorrect email or password."
    }
    return readableMessage(error)
}

private func appError(from error: Error) -> AppError? {
    let nsError = error as NSError
    guard let kotlinException = nsError.kotlinException as? AppErrorException else { return nil }
    return kotlinException.error
}
