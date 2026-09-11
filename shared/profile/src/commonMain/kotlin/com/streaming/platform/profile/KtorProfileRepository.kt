package com.streaming.platform.profile

import com.streaming.platform.common.ProfileId
import com.streaming.platform.network.ApiClient

class KtorProfileRepository(private val apiClient: ApiClient) : ProfileRepository {
    override suspend fun listProfiles(): Result<List<Profile>> = apiClient.get("/profiles")

    override suspend fun createProfile(command: CreateProfileCommand): Result<Profile> =
        apiClient.post("/profiles", command)

    override suspend fun updateProfile(profileId: ProfileId, command: UpdateProfileCommand): Result<Profile> =
        apiClient.put("/profiles/$profileId", command)

    override suspend fun deleteProfile(profileId: ProfileId): Result<Unit> =
        apiClient.delete("/profiles/$profileId")
}
