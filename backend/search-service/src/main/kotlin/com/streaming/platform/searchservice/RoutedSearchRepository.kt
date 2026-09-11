package com.streaming.platform.searchservice

import com.streaming.platform.search.AutocompleteSuggestion
import com.streaming.platform.search.SearchHistoryEntry
import com.streaming.platform.search.SearchQuery
import com.streaming.platform.search.SearchRepository
import com.streaming.platform.search.SearchResult

class RoutedSearchRepository(
    private val database: JdbcSearchRepository,
    private val index: com.streaming.platform.search.SearchIndex?,
) : SearchRepository {
    override suspend fun search(query: SearchQuery, userId: String?): Result<SearchResult> {
        val result = index?.search(query) ?: database.search(query, null)
        if (userId != null && result.isSuccess) database.recordHistory(userId, query.query).getOrThrow()
        return result
    }

    override suspend fun autocomplete(prefix: String, limit: Int): Result<List<AutocompleteSuggestion>> =
        index?.autocomplete(prefix, limit) ?: database.autocomplete(prefix, limit)

    override suspend fun history(userId: String, limit: Int): Result<List<SearchHistoryEntry>> = database.history(userId, limit)

    override suspend fun recordHistory(userId: String, query: String): Result<Unit> = database.recordHistory(userId, query)
}
