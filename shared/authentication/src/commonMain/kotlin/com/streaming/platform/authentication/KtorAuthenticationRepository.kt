package com.streaming.platform.authentication

import com.streaming.platform.common.DeviceId
import com.streaming.platform.network.ApiClient
import com.streaming.platform.security.TokenPair
import kotlinx.serialization.Serializable

@Serializable
private data class RefreshRequest(val refreshToken: String, val deviceId: String)

@Serializable
private data class LogoutRequest(val refreshToken: String)

class KtorAuthenticationRepository(private val apiClient: ApiClient) : AuthenticationRepository {
    override suspend fun register(command: RegisterCommand): Result<AuthenticationResponse> =
        apiClient.post("/auth/register", command)

    override suspend fun login(command: LoginCommand): Result<AuthenticationResponse> =
        apiClient.post("/auth/login", command)

    override suspend fun refresh(refreshToken: String, deviceId: DeviceId): Result<TokenPair> =
        apiClient.post("/auth/refresh", RefreshRequest(refreshToken, deviceId))

    override suspend fun logout(refreshToken: String): Result<Unit> =
        apiClient.post("/auth/logout", LogoutRequest(refreshToken))
}
