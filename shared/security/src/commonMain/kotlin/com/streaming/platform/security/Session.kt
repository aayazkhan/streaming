package com.streaming.platform.security

import com.streaming.platform.common.DeviceId
import com.streaming.platform.common.UserId
import com.streaming.platform.core.AppResult
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: Instant,
)

@Serializable
data class Session(
    val userId: UserId,
    val deviceId: DeviceId,
    val tokens: TokenPair,
)

interface SecureTokenStore {
    suspend fun read(): TokenPair?
    suspend fun write(tokens: TokenPair)
    suspend fun clear()
}

interface SessionRepository {
    suspend fun restore(): AppResult<Session?>
    suspend fun save(session: Session): AppResult<Unit>
    suspend fun clear(): AppResult<Unit>
}
