package com.streaming.platform.authentication

import com.streaming.platform.network.AccessTokenProvider
import com.streaming.platform.security.SecureTokenStore
import com.streaming.platform.security.TokenPair
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

sealed interface AuthState {
    data object Initializing : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: UserSummary?) : AuthState
}

/**
 * Owns the session lifecycle: in-memory access token (never persisted — even though a platform's
 * secure-at-rest store may be trustworthy enough that persisting it would be fine, keeping the
 * access token memory-only anyway means a compromised-at-rest refresh token alone can't be replayed
 * without also compromising the running process), refresh token via [SecureTokenStore], and a
 * refresh-and-retry hook for [AccessTokenProvider] consumers. Shared across every native client —
 * platform layers supply only a [SecureTokenStore] impl and a stable per-install device id.
 */
class SessionManager(
    private val authenticationRepository: AuthenticationRepository,
    private val tokenStore: SecureTokenStore,
    private val deviceIdValue: String,
) : AccessTokenProvider {
    private val deviceId: String get() = deviceIdValue
    private val refreshMutex = Mutex()

    @Volatile private var accessToken: String? = null
    @Volatile private var refreshToken: String? = null

    private val _state = MutableStateFlow<AuthState>(AuthState.Initializing)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    override suspend fun accessToken(): String? = accessToken

    override suspend fun refreshToken(): Boolean = refreshAccessToken()

    suspend fun restoreSession() {
        val stored = tokenStore.read()
        if (stored == null) {
            _state.value = AuthState.SignedOut
            return
        }
        refreshToken = stored.refreshToken
        val refreshed = refreshAccessToken()
        _state.value = if (refreshed) AuthState.SignedIn(null) else AuthState.SignedOut
    }

    suspend fun login(email: String, password: String): Result<Unit> {
        val result = authenticationRepository.login(LoginCommand(email, password, deviceId))
        return result.fold(
            onSuccess = { response ->
                applyTokens(response.session.tokens)
                _state.value = AuthState.SignedIn(response.user)
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) },
        )
    }

    suspend fun register(email: String, password: String, displayName: String): Result<Unit> {
        val result = authenticationRepository.register(RegisterCommand(email, password, displayName, deviceId))
        return result.fold(
            onSuccess = { response ->
                applyTokens(response.session.tokens)
                _state.value = AuthState.SignedIn(response.user)
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) },
        )
    }

    suspend fun logout() {
        val token = refreshToken
        clearSession()
        if (token != null) authenticationRepository.logout(token)
    }

    /** Called by callers after an authenticated request comes back 401 — attempts one refresh. */
    suspend fun refreshAccessToken(): Boolean = refreshMutex.withLock {
        val current = refreshToken ?: return@withLock false
        val result = authenticationRepository.refresh(current, deviceId)
        result.fold(
            onSuccess = { tokens ->
                applyTokens(tokens)
                true
            },
            onFailure = {
                clearSession()
                false
            },
        )
    }

    private suspend fun applyTokens(tokens: TokenPair) {
        accessToken = tokens.accessToken
        refreshToken = tokens.refreshToken
        tokenStore.write(tokens)
    }

    private suspend fun clearSession() {
        accessToken = null
        refreshToken = null
        tokenStore.clear()
        _state.value = AuthState.SignedOut
    }
}
