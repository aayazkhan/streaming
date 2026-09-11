package com.streaming.platform.android.di

import android.content.Context
import com.streaming.platform.android.BuildConfig
import com.streaming.platform.android.auth.ActiveProfileHolder
import com.streaming.platform.android.auth.getOrCreateDeviceId
import com.streaming.platform.authentication.SessionManager
import com.streaming.platform.android.data.AndroidSecureTokenStore
import com.streaming.platform.authentication.KtorAuthenticationRepository
import com.streaming.platform.content.ContentRepository
import com.streaming.platform.content.KtorContentRepository
import com.streaming.platform.network.ApiClient
import com.streaming.platform.network.ApiClientConfig
import com.streaming.platform.playback.KtorPlaybackRepository
import com.streaming.platform.playback.PlaybackRepository
import com.streaming.platform.profile.KtorProfileRepository
import com.streaming.platform.profile.ProfileRepository
import com.streaming.platform.search.KtorSearchRepository
import com.streaming.platform.search.SearchRepository
import com.streaming.platform.watchlist.KtorWatchlistRepository
import com.streaming.platform.watchlist.WatchlistRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Manual DI container — no Hilt/Koin. The dependency graph here is small and static (one
 * implementation per shared repository interface, wired once at process start), so a DI framework
 * would be an abstraction this app doesn't need yet.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false })
            }
            install(Logging) {
                // Ktor's default logger routes through SLF4J, which has no provider on Android
                // (silently swallowed — logged nothing, made a real 8080-port-collision issue much
                // harder to diagnose). This one uses android.util.Log directly.
                logger = object : Logger {
                    override fun log(message: String) {
                        android.util.Log.i("KtorClient", message)
                    }
                }
                level = if (BuildConfig.DEBUG) LogLevel.INFO else LogLevel.NONE
            }
        }
    }

    val tokenStore: AndroidSecureTokenStore by lazy { AndroidSecureTokenStore(appContext) }

    private val apiClientConfig by lazy { ApiClientConfig(baseUrl = BuildConfig.API_BASE_URL) }

    private val authenticationRepository by lazy { KtorAuthenticationRepository(authApiClient) }

    val sessionManager: SessionManager by lazy {
        SessionManager(authenticationRepository, tokenStore, getOrCreateDeviceId(appContext))
    }

    val activeProfileHolder: ActiveProfileHolder by lazy { ActiveProfileHolder(appContext) }

    // Auth endpoints (register/login/refresh/logout) must never attach a bearer token — a stale
    // or expired access token there would fail confusingly instead of the endpoint just working.
    private val authApiClient: ApiClient by lazy { ApiClient(httpClient, apiClientConfig) { null } }

    // Every other client attaches the current access token and is backed by the session manager's
    // own refresh flow at the call-site (see ui/ ViewModels: on AppError.Unauthorized, call
    // sessionManager.refreshAccessToken() once and retry, mirroring apps/webApp's apiClient).
    private val authedApiClient: ApiClient by lazy { ApiClient(httpClient, apiClientConfig, sessionManager) }

    val profileRepository: ProfileRepository by lazy { KtorProfileRepository(authedApiClient) }
    val contentRepository: ContentRepository by lazy { KtorContentRepository(authedApiClient) }
    val searchRepository: SearchRepository by lazy { KtorSearchRepository(authedApiClient) }
    val playbackRepository: PlaybackRepository by lazy { KtorPlaybackRepository(authedApiClient) }
    val watchlistRepository: WatchlistRepository by lazy { KtorWatchlistRepository(authedApiClient) }
}
