package com.streaming.platform.watchlist

import com.streaming.platform.content.ContentSummary
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class WatchlistEntry(
    val content: ContentSummary,
    val addedAt: Instant,
)

interface WatchlistRepository {
    suspend fun list(userId: String, limit: Int, cursor: String?): Result<com.streaming.platform.common.Page<WatchlistEntry>>
    suspend fun add(userId: String, contentId: String): Result<WatchlistEntry>
    suspend fun remove(userId: String, contentId: String): Result<Unit>
}
