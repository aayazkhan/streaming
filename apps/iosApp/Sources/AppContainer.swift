import Foundation
import SharedKit

/// Manual DI container — no third-party DI framework, mirrors androidApp's AppContainer. The
/// dependency graph is small and static (one implementation per shared repository interface,
/// wired once at process start), so a DI framework would be an abstraction this app doesn't need.
final class AppContainer {
    let tokenStore = IosSecureTokenStore()
    let deviceId = IosDeviceIdKt.getOrCreateDeviceId()

    private let httpClient = HttpClientFactoryKt.makeHttpClient(debugLogging: true)

    private lazy var apiClientConfig = ApiClientConfig(baseUrl: AppConfig.apiBaseURL, requestTimeoutMillis: 15_000)

    // Auth endpoints (register/login/refresh/logout) must never attach a bearer token — a stale
    // or expired access token there would fail confusingly instead of the endpoint just working.
    private lazy var authApiClient = ApiClient(httpClient: httpClient, config: apiClientConfig, accessTokenProvider: NoAccessTokenProvider.shared)

    private lazy var authenticationRepository: AuthenticationRepository = KtorAuthenticationRepository(apiClient: authApiClient)

    lazy var sessionManager = SessionManager(authenticationRepository: authenticationRepository, tokenStore: tokenStore, deviceIdValue: deviceId)

    // Every other client attaches the current access token and refreshes-and-retries transparently
    // (ApiClient's own logic, see shared/network) — sessionManager itself implements
    // AccessTokenProvider, so it's passed straight through with no adapter needed.
    private lazy var authedApiClient = ApiClient(httpClient: httpClient, config: apiClientConfig, accessTokenProvider: sessionManager)

    lazy var profileRepository: ProfileRepository = KtorProfileRepository(apiClient: authedApiClient)
    lazy var contentRepository: ContentRepository = KtorContentRepository(apiClient: authedApiClient)
    lazy var searchRepository: SearchRepository = KtorSearchRepository(apiClient: authedApiClient)
    lazy var playbackRepository: PlaybackRepository = KtorPlaybackRepository(apiClient: authedApiClient)
    lazy var watchlistRepository: WatchlistRepository = KtorWatchlistRepository(apiClient: authedApiClient)
}

enum AppConfig {
    /// iOS Simulator shares the host machine's network namespace, so "localhost" reaches the same
    /// docker-compose api-gateway container the other clients were verified against — no
    /// Android-style loopback-alias workaround needed here.
    static let apiBaseURL = "http://localhost:8080/v1"
}
