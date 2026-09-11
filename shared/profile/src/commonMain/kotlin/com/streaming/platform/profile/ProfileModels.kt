package com.streaming.platform.profile

import com.streaming.platform.common.ProfileId
import com.streaming.platform.common.UserId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class ProfileKind { STANDARD, KIDS }

@Serializable
data class Profile(
    val id: ProfileId,
    val userId: UserId,
    val name: String,
    val kind: ProfileKind,
    val language: String,
    val subtitleLanguage: String?,
    val avatarKey: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class CreateProfileCommand(
    val name: String,
    val kind: ProfileKind = ProfileKind.STANDARD,
    val language: String = "en",
    val subtitleLanguage: String? = null,
    val avatarKey: String? = null,
)

@Serializable
data class UpdateProfileCommand(
    val name: String,
    val language: String,
    val subtitleLanguage: String?,
    val avatarKey: String?,
)

interface ProfileRepository {
    suspend fun listProfiles(): Result<List<Profile>>
    suspend fun createProfile(command: CreateProfileCommand): Result<Profile>
    suspend fun updateProfile(profileId: ProfileId, command: UpdateProfileCommand): Result<Profile>
    suspend fun deleteProfile(profileId: ProfileId): Result<Unit>
}

class ListProfilesUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(): Result<List<Profile>> = repository.listProfiles()
}

class CreateProfileUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(command: CreateProfileCommand): Result<Profile> = repository.createProfile(command)
}
