package com.streaming.platform.authentication

import com.streaming.platform.common.DeviceId
import com.streaming.platform.security.SecureTokenStore
import com.streaming.platform.security.Session
import com.streaming.platform.security.TokenPair
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RegisterUseCase(private val repository: AuthenticationRepository) {
    suspend operator fun invoke(command: RegisterCommand): Result<AuthenticationResponse> = repository.register(command)
}

class LoginUseCase(private val repository: AuthenticationRepository) {
    suspend operator fun invoke(command: LoginCommand): Result<AuthenticationResponse> = repository.login(command)
}

class RefreshSessionUseCase(private val repository: AuthenticationRepository) {
    suspend operator fun invoke(refreshToken: String, deviceId: DeviceId): Result<TokenPair> =
        repository.refresh(refreshToken, deviceId)
}

class LogoutUseCase(private val repository: AuthenticationRepository, private val tokenStore: SecureTokenStore) {
    suspend operator fun invoke(): Result<Unit> {
        val tokens = tokenStore.read() ?: return Result.success(Unit)
        return repository.logout(tokens.refreshToken).also { result ->
            if (result.isSuccess) tokenStore.clear()
        }
    }
}

sealed interface AuthenticationState {
    data object Loading : AuthenticationState
    data object SignedOut : AuthenticationState
    data class SignedIn(val session: Session) : AuthenticationState
    data class Failed(val message: String) : AuthenticationState
}

class AuthenticationStateStore {
    private val mutableState = MutableStateFlow<AuthenticationState>(AuthenticationState.Loading)
    val state: StateFlow<AuthenticationState> = mutableState.asStateFlow()

    fun setSignedIn(session: Session) = mutableState.tryEmit(AuthenticationState.SignedIn(session))
    fun setSignedOut() = mutableState.tryEmit(AuthenticationState.SignedOut)
    fun setFailed(message: String) = mutableState.tryEmit(AuthenticationState.Failed(message))
}
