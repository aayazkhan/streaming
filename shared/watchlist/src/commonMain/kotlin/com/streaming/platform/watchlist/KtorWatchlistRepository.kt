package com.streaming.platform.watchlist

import com.streaming.platform.common.Page
import com.streaming.platform.network.ApiClient
import com.streaming.platform.network.queryOf

class KtorWatchlistRepository(private val apiClient: ApiClient) : WatchlistRepository {
    override suspend fun list(userId: String, limit: Int, cursor: String?): Result<Page<WatchlistEntry>> =
        apiClient.get("/watchlist", queryOf("limit" to limit, "cursor" to cursor))

    override suspend fun add(userId: String, contentId: String): Result<WatchlistEntry> =
        apiClient.put("/watchlist/$contentId", Unit)

    override suspend fun remove(userId: String, contentId: String): Result<Unit> =
        apiClient.delete("/watchlist/$contentId")
}
