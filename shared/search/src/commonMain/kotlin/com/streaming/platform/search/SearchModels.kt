package com.streaming.platform.search

import com.streaming.platform.content.ContentSummary
import com.streaming.platform.content.ContentType
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class SearchQuery(
    val query: String,
    val type: ContentType? = null,
    val genre: String? = null,
    val releaseYear: Int? = null,
    val cursor: String? = null,
    val limit: Int = 20,
)

@Serializable
data class SearchResult(
    val items: List<ContentSummary>,
    val nextCursor: String? = null,
    val total: Long? = null,
)

@Serializable
data class AutocompleteSuggestion(
    val text: String,
    val contentId: String,
    val type: ContentType,
)

@Serializable
data class SearchHistoryEntry(
    val query: String,
    val searchedAt: Instant,
)

interface SearchRepository {
    suspend fun search(query: SearchQuery, userId: String?): Result<SearchResult>
    suspend fun autocomplete(prefix: String, limit: Int): Result<List<AutocompleteSuggestion>>
    suspend fun history(userId: String, limit: Int): Result<List<SearchHistoryEntry>>
    suspend fun recordHistory(userId: String, query: String): Result<Unit>
}

interface SearchIndex {
    suspend fun search(query: SearchQuery): Result<SearchResult>
    suspend fun autocomplete(prefix: String, limit: Int): Result<List<AutocompleteSuggestion>>
}
