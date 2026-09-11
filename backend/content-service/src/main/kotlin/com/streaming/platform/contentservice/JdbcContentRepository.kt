package com.streaming.platform.contentservice

import com.streaming.platform.common.ContentId
import com.streaming.platform.common.Page
import com.streaming.platform.content.AccessTier
import com.streaming.platform.content.AdminContentSummary
import com.streaming.platform.content.AudioTrack
import com.streaming.platform.content.ContentDetail
import com.streaming.platform.content.ContentQuery
import com.streaming.platform.content.ContentRepository
import com.streaming.platform.content.ContentSection
import com.streaming.platform.content.ContentStatus
import com.streaming.platform.content.ContentSummary
import com.streaming.platform.content.ContentType
import com.streaming.platform.content.CreateContentCommand
import com.streaming.platform.content.HomeFeed
import com.streaming.platform.content.PersonCredit
import com.streaming.platform.content.SubtitleTrack
import com.streaming.platform.content.UpdateContentCommand
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Instant
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

class JdbcContentRepository(
    private val dataSource: DataSource,
    private val publicAssetBaseUrl: String?,
) : ContentRepository {
    override suspend fun findById(contentId: ContentId): Result<ContentDetail> = runCatching {
        val id = parseId(contentId)
        val summary = dataSource.connection.use { connection ->
            connection.prepareStatement("$SUMMARY_SELECT WHERE c.id = ? AND c.status = 'PUBLISHED'").use { statement ->
                statement.setObject(1, id)
                statement.executeQuery().use { result ->
                    if (!result.next()) throw notFound()
                    result.toSummary()
                }
            }
        }
        val genres = queryStrings(id, "SELECT g.name FROM genres g JOIN content_genres cg ON cg.genre_id = g.id WHERE cg.content_id = ? ORDER BY g.name")
        val credits = dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT name, role, character_name FROM content_people WHERE content_id = ? ORDER BY billing_order ASC, name ASC").use { statement ->
                statement.setObject(1, id)
                statement.executeQuery().use { result ->
                    buildList { while (result.next()) add(PersonCredit(result.getString("name"), result.getString("role"), result.getString("character_name"))) }
                }
            }
        }
        val audioTracks = dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT language, label, is_default FROM content_audio_tracks WHERE content_id = ? ORDER BY is_default DESC, language ASC").use { statement ->
                statement.setObject(1, id)
                statement.executeQuery().use { result ->
                    buildList { while (result.next()) add(AudioTrack(result.getString("language"), result.getString("label"), result.getBoolean("is_default"))) }
                }
            }
        }
        val subtitleTracks = dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT language, label, uri FROM content_subtitles WHERE content_id = ? ORDER BY language ASC").use { statement ->
                statement.setObject(1, id)
                statement.executeQuery().use { result ->
                    buildList { while (result.next()) add(SubtitleTrack(result.getString("language"), result.getString("label"), result.getString("uri"))) }
                }
            }
        }
        val children = dataSource.connection.use { connection ->
            connection.prepareStatement("$SUMMARY_SELECT WHERE c.parent_id = ? AND c.status = 'PUBLISHED' ORDER BY c.season_number NULLS FIRST, c.episode_number NULLS FIRST, c.title ASC").use { statement ->
                statement.setObject(1, id)
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toSummary()) } }
            }
        }
        val playable = dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT EXISTS(SELECT 1 FROM media_assets WHERE content_id = ? AND status = 'READY')").use { statement ->
                statement.setObject(1, id)
                statement.executeQuery().use { result -> result.next() && result.getBoolean(1) }
            }
        }
        ContentDetail(summary, genres, credits, audioTracks, subtitleTracks, children, playable)
    }

    override suspend fun list(query: ContentQuery): Result<Page<ContentSummary>> = runCatching {
        val limit = query.limit.coerceIn(1, 50)
        val rows = executeList(query, "c.created_at DESC, c.id DESC", limit + 1)
        val hasMore = rows.size > limit
        val items = rows.take(limit)
        Page(items, items.lastOrNull()?.createdAt?.toEpochMilliseconds()?.toString(), hasMore)
    }

    override suspend fun home(): Result<HomeFeed> = runCatching {
        HomeFeed(
            sections = listOf(
                ContentSection("featured", "Featured", executeList(ContentQuery(limit = 20), "c.featured_rank ASC NULLS LAST, c.created_at DESC", 20, "c.is_featured = TRUE")),
                ContentSection("trending", "Trending", executeList(ContentQuery(limit = 20), "c.trending_score DESC, c.created_at DESC", 20)),
                ContentSection("new-releases", "New Releases", executeList(ContentQuery(limit = 20), "c.release_date DESC NULLS LAST, c.created_at DESC", 20)),
                ContentSection("recently-added", "Recently Added", executeList(ContentQuery(limit = 20), "c.created_at DESC", 20)),
                ContentSection("popular", "Popular", executeList(ContentQuery(limit = 20), "c.popularity_score DESC, c.created_at DESC", 20)),
            ).filter { it.items.isNotEmpty() },
        )
    }

    override suspend fun similar(contentId: ContentId, limit: Int): Result<List<ContentSummary>> = runCatching {
        val id = parseId(contentId)
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "$SUMMARY_SELECT JOIN content_genres candidate_genres ON candidate_genres.content_id = c.id WHERE c.status = 'PUBLISHED' AND c.id <> ? AND candidate_genres.genre_id IN (SELECT genre_id FROM content_genres WHERE content_id = ?) GROUP BY c.id, c.type, c.title, c.synopsis, c.poster_key, c.backdrop_key, c.release_year, c.duration_seconds, c.access_tier, c.rating, c.created_at ORDER BY COUNT(*) DESC, c.popularity_score DESC LIMIT ?",
            ).use { statement ->
                statement.setObject(1, id)
                statement.setObject(2, id)
                statement.setInt(3, limit.coerceIn(1, 50))
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toSummary()) } }
            }
        }
    }

    // --- admin (write-side) methods below. Consumer-facing methods above are untouched and keep
    // their hardcoded `status = 'PUBLISHED'` filter — these bypass it and are only ever reachable
    // from admin-gated routes (see AdminContentRoutes.kt).

    fun createContent(command: CreateContentCommand): AdminContentSummary {
        val id = UUID.randomUUID()
        val now = OffsetDateTime.now()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO contents(id, type, status, access_tier, title, synopsis, release_year, duration_seconds, is_featured, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setObject(1, id)
                statement.setString(2, command.type.name)
                statement.setString(3, command.status.name)
                statement.setString(4, command.accessTier.name)
                statement.setString(5, command.title.trim())
                statement.setString(6, command.synopsis?.trim())
                command.releaseYear?.let { statement.setInt(7, it) } ?: statement.setNull(7, java.sql.Types.INTEGER)
                command.durationSeconds?.let { statement.setInt(8, it) } ?: statement.setNull(8, java.sql.Types.INTEGER)
                statement.setBoolean(9, command.isFeatured)
                statement.setObject(10, now)
                statement.setObject(11, now)
                statement.executeUpdate()
            }
        }
        replaceGenres(id, command.genreNames)
        return requireAdminSummary(id)
    }

    fun updateContent(contentId: ContentId, command: UpdateContentCommand): AdminContentSummary {
        val id = parseId(contentId)
        val now = OffsetDateTime.now()
        val updated = dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE contents SET title = ?, synopsis = ?, release_year = ?, duration_seconds = ?, access_tier = ?, status = ?, is_featured = ?, updated_at = ? WHERE id = ?",
            ).use { statement ->
                statement.setString(1, command.title.trim())
                statement.setString(2, command.synopsis?.trim())
                command.releaseYear?.let { statement.setInt(3, it) } ?: statement.setNull(3, java.sql.Types.INTEGER)
                command.durationSeconds?.let { statement.setInt(4, it) } ?: statement.setNull(4, java.sql.Types.INTEGER)
                statement.setString(5, command.accessTier.name)
                statement.setString(6, command.status.name)
                statement.setBoolean(7, command.isFeatured)
                statement.setObject(8, now)
                statement.setObject(9, id)
                statement.executeUpdate() == 1
            }
        }
        if (!updated) throw notFound()
        replaceGenres(id, command.genreNames)
        return requireAdminSummary(id)
    }

    fun archiveContent(contentId: ContentId) {
        val id = parseId(contentId)
        val archived = dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE contents SET status = 'ARCHIVED', updated_at = ? WHERE id = ?").use { statement ->
                statement.setObject(1, OffsetDateTime.now())
                statement.setObject(2, id)
                statement.executeUpdate() == 1
            }
        }
        if (!archived) throw notFound()
    }

    fun findByIdForAdmin(contentId: ContentId): AdminContentSummary {
        val id = parseId(contentId)
        return requireAdminSummary(id)
    }

    fun listForAdmin(query: ContentQuery, status: ContentStatus?): Page<AdminContentSummary> {
        val limit = query.limit.coerceIn(1, 50)
        val conditions = mutableListOf<String>()
        val values = mutableListOf<Any>()
        status?.let { conditions += "c.status = ?"; values += it.name }
        query.type?.let { conditions += "c.type = ?"; values += it.name }
        query.cursor?.let {
            val epoch = it.toLongOrNull() ?: throw ContentException(HttpStatusCode.BadRequest, "INVALID_CURSOR", "Cursor is invalid")
            conditions += "c.created_at < ?"
            values += OffsetDateTime.parse(Instant.fromEpochMilliseconds(epoch).toString())
        }
        val where = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        val sql = "$ADMIN_SUMMARY_SELECT $where ORDER BY c.created_at DESC, c.id DESC LIMIT ?"
        val rows = dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                var index = 1
                values.forEach { value ->
                    when (value) {
                        is String -> statement.setString(index, value)
                        is OffsetDateTime -> statement.setObject(index, value)
                        else -> error("Unsupported query parameter")
                    }
                    index += 1
                }
                statement.setInt(index, limit + 1)
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toAdminSummary()) } }
            }
        }
        val hasMore = rows.size > limit
        val items = rows.take(limit)
        return Page(items, items.lastOrNull()?.summary?.createdAt?.toEpochMilliseconds()?.toString(), hasMore)
    }

    fun listGenres(): List<com.streaming.platform.content.Genre> = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT id, name FROM genres ORDER BY name ASC").use { statement ->
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(com.streaming.platform.content.Genre(result.getObject("id", UUID::class.java).toString(), result.getString("name")))
                }
            }
        }
    }

    private fun requireAdminSummary(id: UUID): AdminContentSummary = dataSource.connection.use { connection ->
        connection.prepareStatement("$ADMIN_SUMMARY_SELECT WHERE c.id = ?").use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { result -> if (result.next()) result.toAdminSummary() else throw notFound() }
        }
    }

    private fun replaceGenres(contentId: UUID, genreNames: List<String>) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM content_genres WHERE content_id = ?").use { statement ->
                statement.setObject(1, contentId)
                statement.executeUpdate()
            }
            genreNames.map(String::trim).filter(String::isNotEmpty).distinctBy { it.lowercase() }.forEach { name ->
                val genreId = connection.prepareStatement(
                    "INSERT INTO genres(id, name, slug) VALUES (?, ?, ?) ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name RETURNING id",
                ).use { statement ->
                    statement.setObject(1, UUID.randomUUID())
                    statement.setString(2, name)
                    statement.setString(3, slugify(name))
                    statement.executeQuery().use { result -> result.next(); result.getObject(1, UUID::class.java) }
                }
                connection.prepareStatement(
                    "INSERT INTO content_genres(content_id, genre_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                ).use { statement ->
                    statement.setObject(1, contentId)
                    statement.setObject(2, genreId)
                    statement.executeUpdate()
                }
            }
        }
    }

    private fun slugify(name: String): String = name.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifEmpty { "genre-${UUID.randomUUID()}" }

    private fun ResultSet.toAdminSummary() = AdminContentSummary(toSummary(), ContentStatus.valueOf(getString("status")))

    private fun executeList(query: ContentQuery, orderBy: String, limit: Int, extraWhere: String? = null): List<ContentSummary> {
        val conditions = mutableListOf("c.status = 'PUBLISHED'")
        val values = mutableListOf<Any>()
        query.type?.let { conditions += "c.type = ?"; values += it.name }
        query.genre?.let { conditions += "EXISTS (SELECT 1 FROM content_genres cg JOIN genres g ON g.id = cg.genre_id WHERE cg.content_id = c.id AND lower(g.name) = lower(?))"; values += it }
        query.releaseYear?.let { conditions += "c.release_year = ?"; values += it }
        query.cursor?.let {
            val epoch = it.toLongOrNull() ?: throw ContentException(HttpStatusCode.BadRequest, "INVALID_CURSOR", "Cursor is invalid")
            conditions += "c.created_at < ?"
            values += OffsetDateTime.parse(Instant.fromEpochMilliseconds(epoch).toString())
        }
        extraWhere?.let(conditions::add)
        val sql = "$SUMMARY_SELECT WHERE ${conditions.joinToString(" AND ")} ORDER BY $orderBy LIMIT ?"
        return dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                var index = 1
                values.forEach { value ->
                    when (value) {
                        is Int -> statement.setInt(index, value)
                        is String -> statement.setString(index, value)
                        is OffsetDateTime -> statement.setObject(index, value)
                        else -> error("Unsupported query parameter")
                    }
                    index += 1
                }
                statement.setInt(index, limit)
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toSummary()) } }
            }
        }
    }

    private fun queryStrings(id: UUID, sql: String): List<String> = dataSource.connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { result -> buildList { while (result.next()) add(result.getString(1)) } }
        }
    }

    private fun ResultSet.toSummary() = ContentSummary(
        id = getObject("id", UUID::class.java).toString(),
        type = ContentType.valueOf(getString("type")),
        title = getString("title"),
        synopsis = getString("synopsis"),
        posterUrl = assetUrl(getString("poster_key"), "images"),
        backdropUrl = assetUrl(getString("backdrop_key"), "images"),
        releaseYear = getObject("release_year")?.let { (it as Number).toInt() },
        durationSeconds = getObject("duration_seconds")?.let { (it as Number).toInt() },
        accessTier = AccessTier.valueOf(getString("access_tier")),
        rating = getObject("rating")?.let { (it as Number).toDouble() },
        createdAt = Instant.parse(getObject("created_at", OffsetDateTime::class.java).toInstant().toString()),
    )

    private fun assetUrl(key: String?, prefix: String): String? = key?.let {
        publicAssetBaseUrl?.trimEnd('/')?.let { base -> "$base/$prefix/${it.trimStart('/')}" }
    }

    private fun parseId(value: String): UUID = runCatching { UUID.fromString(value) }.getOrElse {
        throw ContentException(HttpStatusCode.BadRequest, "INVALID_CONTENT_ID", "Content id is invalid")
    }

    private fun notFound() = ContentException(HttpStatusCode.NotFound, "CONTENT_NOT_FOUND", "Content was not found")

    private companion object {
        const val SUMMARY_SELECT = "SELECT c.id, c.type, c.title, c.synopsis, c.poster_key, c.backdrop_key, c.release_year, c.duration_seconds, c.access_tier, c.rating, c.created_at FROM contents c"
        const val ADMIN_SUMMARY_SELECT = "SELECT c.id, c.type, c.status, c.title, c.synopsis, c.poster_key, c.backdrop_key, c.release_year, c.duration_seconds, c.access_tier, c.rating, c.created_at FROM contents c"
    }
}
