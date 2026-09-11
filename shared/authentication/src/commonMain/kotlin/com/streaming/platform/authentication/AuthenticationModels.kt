package com.streaming.platform.authentication

import com.streaming.platform.common.DeviceId
import com.streaming.platform.common.UserId
import com.streaming.platform.security.Session
import com.streaming.platform.security.TokenPair
import kotlinx.serialization.Serializable

@Serializable
data class RegisterCommand(
    val email: String,
    val password: String,
    val displayName: String,
    val deviceId: DeviceId,
)

@Serializable
data class LoginCommand(
    val email: String,
    val password: String,
    val deviceId: DeviceId,
)

@Serializable
data class UserSummary(
    val id: UserId,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean,
)

@Serializable
data class AuthenticationResponse(
    val user: UserSummary,
    val session: Session,
)

interface AuthenticationRepository {
    suspend fun register(command: RegisterCommand): Result<AuthenticationResponse>
    suspend fun login(command: LoginCommand): Result<AuthenticationResponse>
    suspend fun refresh(refreshToken: String, deviceId: DeviceId): Result<TokenPair>
    suspend fun logout(refreshToken: String): Result<Unit>
}
