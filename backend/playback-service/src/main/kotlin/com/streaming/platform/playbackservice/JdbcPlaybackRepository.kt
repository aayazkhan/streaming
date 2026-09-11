package com.streaming.platform.playbackservice

import com.streaming.platform.common.ContentId
import com.streaming.platform.common.ProfileId
import com.streaming.platform.content.AccessTier
import com.streaming.platform.content.ContentSummary
import com.streaming.platform.content.ContentType
import com.streaming.platform.playback.ContinueWatchingItem
import com.streaming.platform.playback.PlaybackEventType
import com.streaming.platform.playback.PlaybackGrant
import com.streaming.platform.playback.PlaybackPosition
import com.streaming.platform.playback.PlaybackRepository
import com.streaming.platform.playback.PlaybackSession
import com.streaming.platform.playback.PlaybackTelemetryEvent
import com.streaming.platform.playback.StartPlaybackCommand
import com.streaming.platform.playback.UpdatePositionCommand
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

class JdbcPlaybackRepository(
    private val dataSource: DataSource,
    private val config: PlaybackConfig,
    private val tokenSigner: PlaybackTokenSigner,
) : PlaybackRepository {
    fun isGrantActive(claims: PlaybackGrantClaims): Boolean = runCatching {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT 1 FROM playback_sessions WHERE id = ? AND user_id = ? AND profile_id = ? AND content_id = ? AND ended_at IS NULL AND expires_at > CURRENT_TIMESTAMP",
            ).use { statement ->
                statement.setObject(1, UUID.fromString(claims.sessionId))
                statement.setObject(2, UUID.fromString(claims.userId))
                statement.setObject(3, UUID.fromString(claims.profileId))
                statement.setObject(4, UUID.fromString(claims.contentId))
                statement.executeQuery().use { it.next() }
            }
        }
    }.getOrDefault(false)

    override suspend fun start(command: StartPlaybackCommand, userId: String): Result<PlaybackGrant> = runCatching {
        val userUuid = parseUuid(userId, "INVALID_USER_ID")
        val profileUuid = parseUuid(command.profileId, "INVALID_PROFILE_ID")
        val contentUuid = parseUuid(command.contentId, "INVALID_CONTENT_ID")
        val media = dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT c.access_tier, c.duration_seconds, m.hls_manifest_key, m.dash_manifest_key FROM contents c JOIN media_assets m ON m.content_id = c.id JOIN profiles p ON p.id = ? AND p.user_id = ? WHERE c.id = ? AND c.status = 'PUBLISHED' AND m.status = 'READY'",
            ).use { statement ->
                statement.setObject(1, profileUuid)
                statement.setObject(2, userUuid)
                statement.setObject(3, contentUuid)
                statement.executeQuery().use { result -> if (result.next()) result.toMedia() else null }
            }
        } ?: throw PlaybackException(HttpStatusCode.NotFound, "CONTENT_NOT_PLAYABLE", "Content is unavailable for playback")
        if (media.accessTier == AccessTier.PREMIUM) {
            throw PlaybackException(HttpStatusCode.Forbidden, "ENTITLEMENT_REQUIRED", "An active entitlement is required for this content")
        }
        if (media.hlsKey == null && media.dashKey == null) {
            throw PlaybackException(HttpStatusCode.Conflict, "MEDIA_NOT_READY", "No playable manifest is available")
        }
        val now = Clock.System.now()
        val expiresAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + config.sessionTtl.toMillis())
        val sessionId = UUID.randomUUID().toString()
        dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO playback_sessions(id, user_id, profile_id, content_id, started_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)").use { statement ->
                statement.setObject(1, UUID.fromString(sessionId))
                statement.setObject(2, userUuid)
                statement.setObject(3, profileUuid)
                statement.setObject(4, contentUuid)
                statement.setObject(5, toOffsetDateTime(now))
                statement.setObject(6, toOffsetDateTime(expiresAt))
                statement.executeUpdate()
            }
        }
        val token = tokenSigner.issue(
            userId,
            command.profileId,
            command.contentId,
            sessionId,
            expiresAt,
            listOfNotNull(media.hlsKey, media.dashKey),
            listOfNotNull(media.hlsKey, media.dashKey).mapNotNull { it.substringBeforeLast('/', "").takeIf(String::isNotBlank) },
        )
        val session = PlaybackSession(sessionId, userId, command.profileId, command.contentId, now, expiresAt)
        PlaybackGrant(
            session = session,
            hlsUrl = media.hlsKey?.let { signedUrl(it, token) },
            dashUrl = media.dashKey?.let { signedUrl(it, token) },
            playbackToken = token,
            expiresAt = expiresAt,
        )
    }

    override suspend fun updatePosition(sessionId: String, userId: String, command: UpdatePositionCommand): Result<PlaybackPosition> = runCatching {
        require(command.positionSeconds >= 0) { "Position cannot be negative" }
        val sessionUuid = parseUuid(sessionId, "INVALID_SESSION_ID")
        val userUuid = parseUuid(userId, "INVALID_USER_ID")
        val now = Clock.System.now()
        val session = dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT profile_id, content_id FROM playback_sessions WHERE id = ? AND user_id = ? AND ended_at IS NULL AND expires_at > CURRENT_TIMESTAMP").use { statement ->
                statement.setObject(1, sessionUuid)
                statement.setObject(2, userUuid)
                statement.executeQuery().use { result -> if (result.next()) result.getObject("profile_id", UUID::class.java) to result.getObject("content_id", UUID::class.java) else null }
            }
        } ?: throw PlaybackException(HttpStatusCode.NotFound, "SESSION_NOT_FOUND", "Playback session was not found or expired")
        dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO watch_progress(user_id, profile_id, content_id, session_id, position_seconds, duration_seconds, completed, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (user_id, profile_id, content_id) DO UPDATE SET session_id = EXCLUDED.session_id, position_seconds = EXCLUDED.position_seconds, duration_seconds = EXCLUDED.duration_seconds, completed = EXCLUDED.completed, updated_at = EXCLUDED.updated_at").use { statement ->
                statement.setObject(1, userUuid)
                statement.setObject(2, session.first)
                statement.setObject(3, session.second)
                statement.setObject(4, sessionUuid)
                statement.setLong(5, command.positionSeconds)
                command.durationSeconds?.let { statement.setLong(6, it) } ?: statement.setNull(6, java.sql.Types.BIGINT)
                statement.setBoolean(7, command.completed)
                statement.setObject(8, toOffsetDateTime(now))
                statement.executeUpdate()
            }
        }
        PlaybackPosition(sessionId, session.second.toString(), command.positionSeconds, command.durationSeconds, command.completed, now)
    }

    override suspend fun recordEvent(userId: String, event: PlaybackTelemetryEvent): Result<Unit> = runCatching {
        val userUuid = parseUuid(userId, "INVALID_USER_ID")
        val sessionUuid = parseUuid(event.sessionId, "INVALID_SESSION_ID")
        dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO playback_events(session_id, user_id, event_type, position_seconds, quality, error_code, occurred_at, metadata) SELECT id, user_id, ?, ?, ?, ?, ?, ?::jsonb FROM playback_sessions WHERE id = ? AND user_id = ?").use { statement ->
                statement.setString(1, event.type.name)
                event.positionSeconds?.let { statement.setLong(2, it) } ?: statement.setNull(2, java.sql.Types.BIGINT)
                statement.setString(3, event.quality)
                statement.setString(4, event.errorCode)
                statement.setObject(5, toOffsetDateTime(event.occurredAt))
                statement.setString(6, Json.encodeToString(event.metadata))
                statement.setObject(7, sessionUuid)
                statement.setObject(8, userUuid)
                if (statement.executeUpdate() != 1) throw PlaybackException(HttpStatusCode.NotFound, "SESSION_NOT_FOUND", "Playback session was not found")
            }
        }
    }

    override suspend fun continueWatching(userId: String, profileId: ProfileId?, limit: Int): Result<List<ContinueWatchingItem>> = runCatching {
        val userUuid = parseUuid(userId, "INVALID_USER_ID")
        val profileUuid = profileId?.let { parseUuid(it, "INVALID_PROFILE_ID") }
        val sql = "SELECT p.session_id, p.content_id, p.position_seconds, p.duration_seconds, p.completed, p.updated_at, c.id, c.type, c.title, c.synopsis, c.poster_key, c.backdrop_key, c.release_year, c.duration_seconds AS content_duration_seconds, c.access_tier, c.rating, c.created_at FROM watch_progress p JOIN contents c ON c.id = p.content_id WHERE p.user_id = ? AND p.completed = FALSE AND c.status = 'PUBLISHED'${if (profileUuid != null) " AND p.profile_id = ?" else ""} ORDER BY p.updated_at DESC LIMIT ?"
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                var index = 1
                statement.setObject(index++, userUuid)
                profileUuid?.let { statement.setObject(index++, it) }
                statement.setInt(index, limit.coerceIn(1, 50))
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            val content = result.toContentSummary()
                            val position = PlaybackPosition(
                                sessionId = result.getObject("session_id", UUID::class.java).toString(),
                                contentId = content.id,
                                positionSeconds = result.getLong("position_seconds"),
                                durationSeconds = result.getObject("duration_seconds")?.let { (it as Number).toLong() },
                                completed = result.getBoolean("completed"),
                                updatedAt = toInstant(result.getObject("updated_at", OffsetDateTime::class.java)),
                            )
                            add(ContinueWatchingItem(content, position))
                        }
                    }
                }
            }
        }
    }

    private fun ResultSet.toMedia() = MediaRow(AccessTier.valueOf(getString("access_tier")), getObject("duration_seconds")?.let { (it as Number).toLong() }, getString("hls_manifest_key"), getString("dash_manifest_key"))

    private fun ResultSet.toContentSummary() = ContentSummary(
        id = getObject("id", UUID::class.java).toString(),
        type = ContentType.valueOf(getString("type")),
        title = getString("title"),
        synopsis = getString("synopsis"),
        posterUrl = getString("poster_key"),
        backdropUrl = getString("backdrop_key"),
        releaseYear = getObject("release_year")?.let { (it as Number).toInt() },
        durationSeconds = getObject("content_duration_seconds")?.let { (it as Number).toInt() },
        accessTier = AccessTier.valueOf(getString("access_tier")),
        rating = getObject("rating")?.let { (it as Number).toDouble() },
        createdAt = toInstant(getObject("created_at", OffsetDateTime::class.java)),
    )

    // The manifest key (e.g. "media/<contentId>/<uploadId>/hls/index.m3u8") already encodes its own
    // protocol subpath — an extra "/hls/" or "/dash/" segment inserted ahead of it doesn't correspond
    // to anything in actual storage layout and made every playback URL this backend issues 404.
    private fun signedUrl(key: String, token: String): String = "${config.cdnBaseUrl.trimEnd('/')}/${key.trimStart('/')}?token=$token"
    private fun toOffsetDateTime(value: Instant): OffsetDateTime = value.toJavaInstant().atOffset(java.time.ZoneOffset.UTC)
    private fun toInstant(value: OffsetDateTime): Instant = Instant.parse(value.toInstant().toString())
    private fun parseUuid(value: String, code: String): UUID = runCatching { UUID.fromString(value) }.getOrElse { throw PlaybackException(HttpStatusCode.BadRequest, code, "Identifier is invalid") }
    private data class MediaRow(val accessTier: AccessTier, val durationSeconds: Long?, val hlsKey: String?, val dashKey: String?)
    private fun Instant.toJavaInstant(): java.time.Instant = java.time.Instant.ofEpochMilli(toEpochMilliseconds())
}
