package com.streaming.platform.playback

import com.streaming.platform.common.ProfileId
import com.streaming.platform.network.ApiClient
import com.streaming.platform.network.queryOf

class KtorPlaybackRepository(private val apiClient: ApiClient) : PlaybackRepository {
    override suspend fun start(command: StartPlaybackCommand, userId: String): Result<PlaybackGrant> =
        apiClient.post("/playback/sessions", command)

    override suspend fun updatePosition(sessionId: String, userId: String, command: UpdatePositionCommand): Result<PlaybackPosition> =
        apiClient.patch("/playback/sessions/$sessionId/position", command)

    override suspend fun recordEvent(userId: String, event: PlaybackTelemetryEvent): Result<Unit> =
        apiClient.post("/playback/sessions/${event.sessionId}/events", event)

    override suspend fun continueWatching(userId: String, profileId: ProfileId?, limit: Int): Result<List<ContinueWatchingItem>> =
        apiClient.get("/continue-watching", queryOf("profileId" to profileId, "limit" to limit))
}
