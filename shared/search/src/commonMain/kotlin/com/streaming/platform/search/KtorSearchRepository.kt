package com.streaming.platform.search

import com.streaming.platform.network.ApiClient
import com.streaming.platform.network.queryOf

class KtorSearchRepository(private val apiClient: ApiClient) : SearchRepository {
    override suspend fun search(query: SearchQuery, userId: String?): Result<SearchResult> = apiClient.get(
        "/search",
        queryOf(
            "q" to query.query,
            "type" to query.type?.name,
            "genre" to query.genre,
            "releaseYear" to query.releaseYear,
            "cursor" to query.cursor,
            "limit" to query.limit,
        ),
    )

    override suspend fun autocomplete(prefix: String, limit: Int): Result<List<AutocompleteSuggestion>> =
        apiClient.get("/search/autocomplete", queryOf("q" to prefix, "limit" to limit))

    override suspend fun history(userId: String, limit: Int): Result<List<SearchHistoryEntry>> =
        apiClient.get("/search/history", queryOf("limit" to limit))

    // No standalone "record history" endpoint exists server-side — SearchRoutes.kt's GET /search
    // records history automatically when the caller is authenticated. Kept as a successful no-op
    // so callers of the shared SearchRepository interface don't need per-client branching.
    override suspend fun recordHistory(userId: String, query: String): Result<Unit> = Result.success(Unit)
}
