package com.streaming.platform.user

import com.streaming.platform.common.UserId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: UserId,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean,
    val createdAt: Instant,
)

interface UserRepository {
    suspend fun getCurrentUser(): Result<User>
    suspend fun updateDisplayName(displayName: String): Result<User>
}

class GetCurrentUserUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(): Result<User> = repository.getCurrentUser()
}
