package com.streaming.platform.watchlistservice

import com.streaming.platform.common.Page
import com.streaming.platform.content.AccessTier
import com.streaming.platform.content.ContentSummary
import com.streaming.platform.content.ContentType
import com.streaming.platform.watchlist.WatchlistEntry
import com.streaming.platform.watchlist.WatchlistRepository
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Instant
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

class JdbcWatchlistRepository(private val dataSource: DataSource) : WatchlistRepository {
    override suspend fun list(userId: String, limit: Int, cursor: String?): Result<Page<WatchlistEntry>> = runCatching {
        val userUuid = parseUser(userId)
        val conditions = mutableListOf("w.user_id = ?", "c.status = 'PUBLISHED'")
        val values = mutableListOf<Any>(userUuid)
        cursor?.let {
            val epoch = it.toLongOrNull() ?: throw WatchlistException(HttpStatusCode.BadRequest, "INVALID_CURSOR", "Watchlist cursor is invalid")
            conditions += "w.added_at < ?"
            values += OffsetDateTime.parse(Instant.fromEpochMilliseconds(epoch).toString())
        }
        val sql = "$SELECT_CONTENT WHERE ${conditions.joinToString(" AND ")} ORDER BY w.added_at DESC, w.content_id DESC LIMIT ?"
        val max = limit.coerceIn(1, 50)
        val rows = dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                var index = 1
                values.forEach { value -> statement.setObject(index++, value) }
                statement.setInt(index, max + 1)
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toEntry()) } }
            }
        }
        val hasMore = rows.size > max
        val items = rows.take(max)
        Page(items, items.lastOrNull()?.addedAt?.toEpochMilliseconds()?.toString(), hasMore)
    }

    override suspend fun add(userId: String, contentId: String): Result<WatchlistEntry> = runCatching {
        val userUuid = parseUser(userId)
        val contentUuid = parseContent(contentId)
        dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO watchlist(user_id, content_id, added_at) SELECT ?, id, CURRENT_TIMESTAMP FROM contents WHERE id = ? AND status = 'PUBLISHED' ON CONFLICT (user_id, content_id) DO NOTHING").use { statement ->
                statement.setObject(1, userUuid)
                statement.setObject(2, contentUuid)
                if (statement.executeUpdate() == 0 && !exists(connection, userUuid, contentUuid)) throw WatchlistException(HttpStatusCode.NotFound, "CONTENT_NOT_FOUND", "Content was not found")
            }
            connection.prepareStatement("$SELECT_CONTENT WHERE w.user_id = ? AND w.content_id = ?").use { statement ->
                statement.setObject(1, userUuid)
                statement.setObject(2, contentUuid)
                statement.executeQuery().use { result -> if (result.next()) result.toEntry() else throw WatchlistException(HttpStatusCode.NotFound, "CONTENT_NOT_FOUND", "Content was not found") }
            }
        }
    }

    override suspend fun remove(userId: String, contentId: String): Result<Unit> = runCatching {
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM watchlist WHERE user_id = ? AND content_id = ?").use { statement ->
                statement.setObject(1, parseUser(userId))
                statement.setObject(2, parseContent(contentId))
                statement.executeUpdate()
            }
        }
    }

    private fun exists(connection: java.sql.Connection, userId: UUID, contentId: UUID): Boolean = connection.prepareStatement("SELECT 1 FROM watchlist WHERE user_id = ? AND content_id = ?").use { statement ->
        statement.setObject(1, userId)
        statement.setObject(2, contentId)
        statement.executeQuery().use { it.next() }
    }

    private fun ResultSet.toEntry() = WatchlistEntry(
        content = ContentSummary(
            id = getObject("content_id", UUID::class.java).toString(),
            type = ContentType.valueOf(getString("type")),
            title = getString("title"),
            synopsis = getString("synopsis"),
            posterUrl = getString("poster_key"),
            backdropUrl = getString("backdrop_key"),
            releaseYear = getObject("release_year")?.let { (it as Number).toInt() },
            durationSeconds = getObject("duration_seconds")?.let { (it as Number).toInt() },
            accessTier = AccessTier.valueOf(getString("access_tier")),
            rating = getObject("rating")?.let { (it as Number).toDouble() },
            createdAt = toInstant(getObject("created_at", OffsetDateTime::class.java)),
        ),
        addedAt = toInstant(getObject("added_at", OffsetDateTime::class.java)),
    )

    private fun parseUser(value: String): UUID = runCatching { UUID.fromString(value) }.getOrElse { throw WatchlistException(HttpStatusCode.BadRequest, "INVALID_USER_ID", "User id is invalid") }
    private fun parseContent(value: String): UUID = runCatching { UUID.fromString(value) }.getOrElse { throw WatchlistException(HttpStatusCode.BadRequest, "INVALID_CONTENT_ID", "Content id is invalid") }
    private fun toInstant(value: OffsetDateTime): Instant = Instant.parse(value.toInstant().toString())

    private companion object {
        const val SELECT_CONTENT = "SELECT w.added_at, c.id AS content_id, c.type, c.title, c.synopsis, c.poster_key, c.backdrop_key, c.release_year, c.duration_seconds, c.access_tier, c.rating, c.created_at FROM watchlist w JOIN contents c ON c.id = w.content_id"
    }
}
