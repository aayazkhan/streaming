package com.streaming.platform.searchservice

import com.streaming.platform.content.AccessTier
import com.streaming.platform.content.ContentSummary
import com.streaming.platform.content.ContentType
import com.streaming.platform.search.AutocompleteSuggestion
import com.streaming.platform.search.SearchHistoryEntry
import com.streaming.platform.search.SearchQuery
import com.streaming.platform.search.SearchRepository
import com.streaming.platform.search.SearchResult
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Instant
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

class JdbcSearchRepository(
    private val dataSource: DataSource,
    private val publicAssetBaseUrl: String?,
) : SearchRepository {
    override suspend fun search(query: SearchQuery, userId: String?): Result<SearchResult> = runCatching {
        val normalized = query.query.trim()
        if (normalized.length !in 2..120) throw SearchException(HttpStatusCode.BadRequest, "INVALID_QUERY", "Search query must be 2 to 120 characters")
        val limit = query.limit.coerceIn(1, 50)
        val searchVector = "to_tsvector('simple', coalesce(c.title, '') || ' ' || coalesce(c.synopsis, ''))"
        val rankExpression = "ts_rank($searchVector, plainto_tsquery('simple', ?))"
        val conditions = mutableListOf("c.status = 'PUBLISHED'", "$searchVector @@ plainto_tsquery('simple', ?)")
        val values = mutableListOf<Any>(normalized)
        query.type?.let { conditions += "c.type = ?"; values += it.name }
        query.genre?.let {
            conditions += "EXISTS (SELECT 1 FROM content_genres cg JOIN genres g ON g.id = cg.genre_id WHERE cg.content_id = c.id AND lower(g.name) = lower(?))"
            values += it
        }
        query.releaseYear?.let { conditions += "c.release_year = ?"; values += it }
        query.cursor?.let { cursor ->
            val parts = cursor.split(':', limit = 2)
            if (parts.size != 2) throw SearchException(HttpStatusCode.BadRequest, "INVALID_CURSOR", "Search cursor is invalid")
            val rank = parts[0].toDoubleOrNull() ?: throw SearchException(HttpStatusCode.BadRequest, "INVALID_CURSOR", "Search cursor is invalid")
            val id = runCatching { UUID.fromString(parts[1]) }.getOrElse {
                throw SearchException(HttpStatusCode.BadRequest, "INVALID_CURSOR", "Search cursor is invalid")
            }
            conditions += "($rankExpression < ? OR ($rankExpression = ? AND c.id > ?))"
            values += normalized
            values += rank
            values += normalized
            values += rank
            values += id
        }
        val sql = "SELECT c.id, c.type, c.title, c.synopsis, c.poster_key, c.backdrop_key, c.release_year, c.duration_seconds, c.access_tier, c.rating, c.created_at, $rankExpression AS search_rank FROM contents c WHERE ${conditions.joinToString(" AND ")} ORDER BY search_rank DESC, c.id ASC LIMIT ?"
        val rows = dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                var index = 1
                statement.setString(index++, normalized)
                values.forEach { value ->
                    when (value) {
                        is Int -> statement.setInt(index, value)
                        is String -> statement.setString(index, value)
                        is Double -> statement.setDouble(index, value)
                        is UUID -> statement.setObject(index, value)
                        else -> error("Unsupported search parameter")
                    }
                    index += 1
                }
                statement.setInt(index, limit + 1)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(SearchRow(result.toSummary(), result.getDouble("search_rank")))
                    }
                }
            }
        }
        if (userId != null) recordHistory(userId, normalized).getOrThrow()
        val hasMore = rows.size > limit
        val items = rows.take(limit)
        SearchResult(items.map { it.summary }, items.lastOrNull()?.let { "${it.rank}:${it.summary.id}" }.takeIf { hasMore })
    }

    override suspend fun autocomplete(prefix: String, limit: Int): Result<List<AutocompleteSuggestion>> = runCatching {
        val normalized = prefix.trim()
        if (normalized.length !in 2..80) return@runCatching emptyList()
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT id, title, type FROM contents WHERE status = 'PUBLISHED' AND lower(title) LIKE lower(?) ORDER BY popularity_score DESC, title ASC LIMIT ?").use { statement ->
                statement.setString(1, "$normalized%")
                statement.setInt(2, limit.coerceIn(1, 20))
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(AutocompleteSuggestion(result.getString("title"), result.getObject("id", UUID::class.java).toString(), ContentType.valueOf(result.getString("type"))))
                    }
                }
            }
        }
    }

    override suspend fun history(userId: String, limit: Int): Result<List<SearchHistoryEntry>> = runCatching {
        val id = parseUserId(userId)
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT query, searched_at FROM search_history WHERE user_id = ? ORDER BY searched_at DESC LIMIT ?").use { statement ->
                statement.setObject(1, id)
                statement.setInt(2, limit.coerceIn(1, 50))
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(SearchHistoryEntry(result.getString("query"), toInstant(result.getObject("searched_at", OffsetDateTime::class.java))))
                    }
                }
            }
        }
    }

    override suspend fun recordHistory(userId: String, query: String): Result<Unit> = runCatching {
        val normalized = query.trim()
        if (normalized.isBlank()) return@runCatching Unit
        dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO search_history(user_id, query, searched_at) VALUES (?, ?, CURRENT_TIMESTAMP) ON CONFLICT (user_id, query) DO UPDATE SET searched_at = CURRENT_TIMESTAMP").use { statement ->
                statement.setObject(1, parseUserId(userId))
                statement.setString(2, normalized.take(120))
                statement.executeUpdate()
            }
        }
    }

    private fun ResultSet.toSummary() = ContentSummary(
        id = getObject("id", UUID::class.java).toString(),
        type = ContentType.valueOf(getString("type")),
        title = getString("title"),
        synopsis = getString("synopsis"),
        posterUrl = assetUrl(getString("poster_key")),
        backdropUrl = assetUrl(getString("backdrop_key")),
        releaseYear = getObject("release_year")?.let { (it as Number).toInt() },
        durationSeconds = getObject("duration_seconds")?.let { (it as Number).toInt() },
        accessTier = AccessTier.valueOf(getString("access_tier")),
        rating = getObject("rating")?.let { (it as Number).toDouble() },
        createdAt = toInstant(getObject("created_at", OffsetDateTime::class.java)),
    )

    private fun parseUserId(value: String): UUID = runCatching { UUID.fromString(value) }.getOrElse {
        throw SearchException(HttpStatusCode.BadRequest, "INVALID_USER_ID", "User id is invalid")
    }

    private fun toInstant(value: OffsetDateTime): Instant = Instant.parse(value.toInstant().toString())

    private fun assetUrl(key: String?): String? = key?.let {
        publicAssetBaseUrl?.trimEnd('/')?.let { base -> "$base/images/${it.trimStart('/')}" }
    }

    private data class SearchRow(val summary: ContentSummary, val rank: Double)
}
