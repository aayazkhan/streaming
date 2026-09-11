package com.streaming.platform.content

import com.streaming.platform.common.ContentId
import com.streaming.platform.common.Page
import com.streaming.platform.network.ApiClient
import com.streaming.platform.network.queryOf

class KtorContentRepository(private val apiClient: ApiClient) : ContentRepository {
    override suspend fun findById(contentId: ContentId): Result<ContentDetail> =
        apiClient.get("/content/$contentId")

    override suspend fun list(query: ContentQuery): Result<Page<ContentSummary>> = apiClient.get(
        "/content",
        queryOf(
            "type" to query.type?.name,
            "genre" to query.genre,
            "releaseYear" to query.releaseYear,
            "cursor" to query.cursor,
            "limit" to query.limit,
        ),
    )

    override suspend fun home(): Result<HomeFeed> = apiClient.get("/home")

    override suspend fun similar(contentId: ContentId, limit: Int): Result<List<ContentSummary>> =
        apiClient.get("/content/$contentId/similar", queryOf("limit" to limit))
}
