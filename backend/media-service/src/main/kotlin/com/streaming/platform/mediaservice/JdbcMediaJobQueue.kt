package com.streaming.platform.mediaservice

import com.streaming.platform.media.MediaJob
import com.streaming.platform.media.MediaJobQueue
import com.streaming.platform.media.MediaJobState
import com.streaming.platform.media.TranscodeProfile
import kotlinx.datetime.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

class JdbcMediaJobQueue(private val dataSource: DataSource) : MediaJobQueue {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun save(job: MediaJob) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """INSERT INTO media_jobs(id, content_id, source_key, state, profiles, failure_code, hls_manifest_key, dash_manifest_key, poster_key, preview_key, subtitle_keys, idempotency_key, attempts, max_attempts, progress_percent, cancel_requested, worker_id, heartbeat_at, next_attempt_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET content_id = EXCLUDED.content_id, source_key = EXCLUDED.source_key, state = EXCLUDED.state, profiles = EXCLUDED.profiles, failure_code = EXCLUDED.failure_code, hls_manifest_key = EXCLUDED.hls_manifest_key, dash_manifest_key = EXCLUDED.dash_manifest_key, poster_key = EXCLUDED.poster_key, preview_key = EXCLUDED.preview_key, subtitle_keys = EXCLUDED.subtitle_keys, idempotency_key = EXCLUDED.idempotency_key, attempts = EXCLUDED.attempts, max_attempts = EXCLUDED.max_attempts, progress_percent = EXCLUDED.progress_percent, cancel_requested = EXCLUDED.cancel_requested, worker_id = EXCLUDED.worker_id, heartbeat_at = EXCLUDED.heartbeat_at, next_attempt_at = EXCLUDED.next_attempt_at, updated_at = EXCLUDED.updated_at""",
            ).use { statement ->
                bind(statement, job)
                statement.executeUpdate()
            }
            syncAssetState(connection, job)
        }
    }

    override suspend fun update(job: MediaJob) = save(job)

    override suspend fun find(jobId: String): MediaJob? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT * FROM media_jobs WHERE id = ?").use { statement ->
            statement.setObject(1, UUID.fromString(jobId))
            statement.executeQuery().use { if (it.next()) it.toJob() else null }
        }
    }

    override suspend fun claimNext(workerId: String, now: Instant): MediaJob? = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            val job = connection.prepareStatement(
                "SELECT * FROM media_jobs WHERE cancel_requested = FALSE AND ((state = 'ACCEPTED' AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP)) OR (state IN ('UPLOADING', 'TRANSCODING', 'PACKAGING', 'SUBTITLES', 'THUMBNAILS') AND heartbeat_at < CURRENT_TIMESTAMP - INTERVAL '2 minutes')) ORDER BY created_at, id FOR UPDATE SKIP LOCKED LIMIT 1",
            ).use { statement -> statement.executeQuery().use { if (it.next()) it.toJob() else null } }
            if (job != null) {
                // Also move state off ACCEPTED, not just worker_id/heartbeat_at — otherwise the row
                // stays eligible for the ACCEPTED branch of the claim query above until the pipeline
                // itself writes a later state, leaving a window where a second concurrent claimer
                // (this worker's own other maxConcurrentJobs slot, or another worker instance) can
                // claim and start processing the exact same job again before that first write lands.
                connection.prepareStatement("UPDATE media_jobs SET worker_id = ?, heartbeat_at = ?, updated_at = ?, state = 'UPLOADING' WHERE id = ?").use { statement ->
                    statement.setString(1, workerId)
                    statement.setObject(2, offset(now))
                    statement.setObject(3, offset(now))
                    statement.setObject(4, UUID.fromString(job.id))
                    statement.executeUpdate()
                }
            }
            connection.commit()
            job?.copy(workerId = workerId, heartbeatAt = now, updatedAt = now, state = MediaJobState.UPLOADING)
        } catch (error: Throwable) {
            connection.rollback()
            throw error
        } finally { connection.autoCommit = true }
    }

    override suspend fun heartbeat(jobId: String, workerId: String, at: Instant): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("UPDATE media_jobs SET heartbeat_at = ?, updated_at = ? WHERE id = ? AND worker_id = ? AND state NOT IN ('READY', 'FAILED', 'CANCELLED')").use { statement ->
            statement.setObject(1, offset(at)); statement.setObject(2, offset(at)); statement.setObject(3, UUID.fromString(jobId)); statement.setString(4, workerId); statement.executeUpdate() == 1
        }
    }

    override suspend fun requestCancellation(jobId: String): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("UPDATE media_jobs SET cancel_requested = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND state NOT IN ('READY', 'FAILED', 'CANCELLED')").use { statement ->
            statement.setObject(1, UUID.fromString(jobId)); statement.executeUpdate() == 1
        }
    }

    override suspend fun complete(job: MediaJob) {
        val completed = job.copy(state = MediaJobState.READY, progressPercent = 100)
        save(completed)
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE media_assets SET status = 'READY', pipeline_stage = 'CDN_PUBLISH', stage_progress_percent = 100, hls_manifest_key = ?, dash_manifest_key = ?, thumbnail_key = ?, preview_key = ?, updated_at = ? WHERE content_id = ?").use { statement ->
                statement.setString(1, completed.hlsManifestKey); statement.setString(2, completed.dashManifestKey); statement.setString(3, completed.posterKey); statement.setString(4, completed.previewKey); statement.setObject(5, OffsetDateTime.parse(completed.updatedAt.toString())); statement.setObject(6, UUID.fromString(completed.contentId)); statement.executeUpdate()
            }
        }
    }

    override suspend fun fail(job: MediaJob, failureCode: String, retryAt: Instant?) {
        val failed = job.copy(state = if (retryAt == null) MediaJobState.FAILED else MediaJobState.ACCEPTED, failureCode = failureCode, nextAttemptAt = retryAt, updatedAt = Instant.parse(OffsetDateTime.now().toInstant().toString()))
        save(failed)
        if (retryAt == null) dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE media_assets SET status = 'FAILED', failure_code = ?, updated_at = ? WHERE content_id = ?").use { statement ->
                statement.setString(1, failureCode.take(200)); statement.setObject(2, OffsetDateTime.parse(failed.updatedAt.toString())); statement.setObject(3, UUID.fromString(failed.contentId)); statement.executeUpdate()
            }
        }
    }

    private fun bind(statement: java.sql.PreparedStatement, job: MediaJob) {
        statement.setObject(1, UUID.fromString(job.id)); statement.setObject(2, UUID.fromString(job.contentId)); statement.setString(3, job.sourceKey); statement.setString(4, job.state.name)
        // failure_code is VARCHAR(200); error messages (exception messages, ffmpeg output) are
        // unbounded and would otherwise throw a SQL error here that crashes the whole worker
        // process (nothing upstream can safely retry a failure-recording write that itself fails).
        statement.setString(5, json.encodeToString(ListSerializer(TranscodeProfile.serializer()), job.profiles)); statement.setString(6, job.failureCode?.take(200)); statement.setString(7, job.hlsManifestKey); statement.setString(8, job.dashManifestKey); statement.setString(9, job.posterKey); statement.setString(10, job.previewKey)
        statement.setString(11, json.encodeToString(MapSerializer(String.serializer(), String.serializer()), job.subtitleKeys)); statement.setString(12, job.idempotencyKey); statement.setInt(13, job.attempts); statement.setInt(14, job.maxAttempts); statement.setInt(15, job.progressPercent); statement.setBoolean(16, job.cancelRequested); statement.setString(17, job.workerId); statement.setObject(18, job.heartbeatAt?.let(::offset)); statement.setObject(19, job.nextAttemptAt?.let(::offset)); statement.setObject(20, offset(job.createdAt)); statement.setObject(21, offset(job.updatedAt))
    }

    private fun syncAssetState(connection: java.sql.Connection, job: MediaJob) {
        val status = when (job.state) {
            MediaJobState.ACCEPTED -> "QUEUED"
            MediaJobState.UPLOADING, MediaJobState.TRANSCODING, MediaJobState.SUBTITLES, MediaJobState.THUMBNAILS -> "PROCESSING"
            MediaJobState.PACKAGING -> "PACKAGING"
            MediaJobState.FAILED -> "FAILED"
            MediaJobState.CANCELLED -> "CANCELLED"
            MediaJobState.READY -> "READY"
        }
        val stage = when (job.state) {
            MediaJobState.TRANSCODING -> "TRANSCODING"
            MediaJobState.PACKAGING -> "PACKAGING"
            MediaJobState.SUBTITLES -> "SUBTITLE"
            MediaJobState.THUMBNAILS -> "THUMBNAIL"
            MediaJobState.READY -> "CDN_PUBLISH"
            else -> null
        }
        connection.prepareStatement("UPDATE media_assets SET status = ?, pipeline_stage = ?, stage_progress_percent = ?, failure_code = ?, updated_at = ? WHERE content_id = ?").use { statement ->
            statement.setString(1, status); statement.setString(2, stage); statement.setInt(3, job.progressPercent.coerceIn(0, 100)); statement.setString(4, job.failureCode?.take(200)); statement.setObject(5, offset(job.updatedAt)); statement.setObject(6, UUID.fromString(job.contentId)); statement.executeUpdate()
        }
    }

    private fun ResultSet.toJob() = MediaJob(
        id = getObject("id", UUID::class.java).toString(), contentId = getObject("content_id", UUID::class.java).toString(), sourceKey = getString("source_key"), state = MediaJobState.valueOf(getString("state")), profiles = json.decodeFromString(ListSerializer(TranscodeProfile.serializer()), getString("profiles")), createdAt = instant(getObject("created_at", OffsetDateTime::class.java)), updatedAt = instant(getObject("updated_at", OffsetDateTime::class.java)), failureCode = getString("failure_code"), hlsManifestKey = getString("hls_manifest_key"), dashManifestKey = getString("dash_manifest_key"), posterKey = getString("poster_key"), previewKey = getString("preview_key"), subtitleKeys = json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), getString("subtitle_keys")), idempotencyKey = getString("idempotency_key"), attempts = getInt("attempts"), maxAttempts = getInt("max_attempts"), progressPercent = getInt("progress_percent"), cancelRequested = getBoolean("cancel_requested"), workerId = getString("worker_id"), heartbeatAt = getObject("heartbeat_at", OffsetDateTime::class.java)?.let(::instant), nextAttemptAt = getObject("next_attempt_at", OffsetDateTime::class.java)?.let(::instant)
    )

    private fun offset(value: Instant) = OffsetDateTime.parse(value.toString())
    private fun instant(value: OffsetDateTime) = Instant.parse(value.toInstant().toString())
}
