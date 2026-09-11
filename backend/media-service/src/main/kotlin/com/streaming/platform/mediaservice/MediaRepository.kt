package com.streaming.platform.mediaservice

import kotlinx.datetime.Instant
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource
import com.streaming.platform.storage.MultipartUploadSession

interface MediaRepository {
    fun createUpload(request: CreateMediaUploadRequest): MediaUpload
    fun findUpload(uploadId: String): MediaUpload?
    fun markUploaded(uploadId: String, completedAt: Instant): MediaUpload
    fun attachMultipartSession(uploadId: String, session: MultipartUploadSession): MediaUpload
    fun markAborted(uploadId: String): MediaUpload
}

class JdbcMediaRepository(private val dataSource: DataSource) : MediaRepository {
    override fun createUpload(request: CreateMediaUploadRequest): MediaUpload {
        val contentId = UUID.fromString(request.contentId)
        val uploadId = UUID.randomUUID()
        val objectKey = "source/$contentId/$uploadId/${safeFileName(request.fileName)}"
        val now = OffsetDateTime.now()
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement("SELECT 1 FROM contents WHERE id = ?").use { statement ->
                    statement.setObject(1, contentId)
                    // require() (IllegalArgumentException), not check() (IllegalStateException) —
                    // MediaRoutes.kt's catch block only maps IllegalArgumentException to 404
                    // CONTENT_NOT_FOUND; check() here was landing in the 409 IllegalStateException
                    // branch instead, producing a wrong status with a mismatched code/message pair.
                    require(statement.executeQuery().use { it.next() }) { "CONTENT_NOT_FOUND" }
                }
                connection.prepareStatement(
                    "INSERT INTO media_uploads(id, content_id, object_key, file_name, content_type, expected_size_bytes, checksum_sha256, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'UPLOADING', ?)",
                ).use { statement ->
                    statement.setObject(1, uploadId)
                    statement.setObject(2, contentId)
                    statement.setString(3, objectKey)
                    statement.setString(4, request.fileName)
                    statement.setString(5, request.contentType)
                    statement.setLong(6, request.sizeBytes)
                    statement.setString(7, request.checksumSha256)
                    statement.setObject(8, now)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    "INSERT INTO media_assets(id, content_id, status, source_key, created_at, updated_at) VALUES (?, ?, 'UPLOADING', ?, ?, ?) " +
                        "ON CONFLICT(content_id) DO UPDATE SET status = 'UPLOADING', source_key = EXCLUDED.source_key, updated_at = EXCLUDED.updated_at",
                ).use { statement ->
                    statement.setObject(1, UUID.randomUUID())
                    statement.setObject(2, contentId)
                    statement.setString(3, objectKey)
                    statement.setObject(4, now)
                    statement.setObject(5, now)
                    statement.executeUpdate()
                }
                connection.commit()
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
        return MediaUpload(uploadId.toString(), request.contentId, objectKey, request.fileName, request.contentType, request.sizeBytes, request.checksumSha256, MediaUploadStatus.UPLOADING, Instant.parse(now.toInstant().toString()))
    }

    override fun findUpload(uploadId: String): MediaUpload? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT id, content_id, object_key, file_name, content_type, expected_size_bytes, checksum_sha256, status, created_at, completed_at, multipart_upload_id, part_size_bytes FROM media_uploads WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, UUID.fromString(uploadId))
            statement.executeQuery().use { result -> if (result.next()) result.toUpload() else null }
        }
    }

    override fun markUploaded(uploadId: String, completedAt: Instant): MediaUpload {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE media_uploads SET status = 'PROCESSING', completed_at = ? WHERE id = ? AND status = 'UPLOADING'").use { statement ->
                statement.setObject(1, OffsetDateTime.parse(completedAt.toString()))
                statement.setObject(2, UUID.fromString(uploadId))
                check(statement.executeUpdate() == 1) { "UPLOAD_NOT_PENDING" }
            }
            connection.prepareStatement("UPDATE media_assets SET status = 'UPLOADED', pipeline_stage = 'VALIDATION', stage_progress_percent = 0, updated_at = ? WHERE source_key = (SELECT object_key FROM media_uploads WHERE id = ?)").use { statement ->
                statement.setObject(1, OffsetDateTime.parse(completedAt.toString()))
                statement.setObject(2, UUID.fromString(uploadId))
                statement.executeUpdate()
            }
        }
        return requireNotNull(findUpload(uploadId))
    }

    override fun attachMultipartSession(uploadId: String, session: MultipartUploadSession): MediaUpload {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE media_uploads SET multipart_upload_id = ?, part_size_bytes = ? WHERE id = ? AND status = 'UPLOADING'").use { statement ->
                statement.setString(1, session.uploadId)
                statement.setLong(2, session.partSizeBytes)
                statement.setObject(3, UUID.fromString(uploadId))
                check(statement.executeUpdate() == 1) { "UPLOAD_NOT_PENDING" }
            }
        }
        return requireNotNull(findUpload(uploadId))
    }

    override fun markAborted(uploadId: String): MediaUpload {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE media_uploads SET status = 'FAILED' WHERE id = ? AND status = 'UPLOADING'").use { statement ->
                statement.setObject(1, UUID.fromString(uploadId))
                check(statement.executeUpdate() == 1) { "UPLOAD_NOT_PENDING" }
            }
        }
        return requireNotNull(findUpload(uploadId))
    }

    private fun ResultSet.toUpload() = MediaUpload(
        id = getObject("id", UUID::class.java).toString(),
        contentId = getObject("content_id", UUID::class.java).toString(),
        objectKey = getString("object_key"),
        fileName = getString("file_name"),
        contentType = getString("content_type"),
        expectedSizeBytes = getLong("expected_size_bytes"),
        checksumSha256 = getString("checksum_sha256"),
        status = MediaUploadStatus.valueOf(getString("status")),
        createdAt = Instant.parse(getObject("created_at", OffsetDateTime::class.java).toInstant().toString()),
        completedAt = getObject("completed_at", OffsetDateTime::class.java)?.let { Instant.parse(it.toInstant().toString()) },
        multipartUploadId = getString("multipart_upload_id"),
        partSizeBytes = getObject("part_size_bytes")?.let { (it as Number).toLong() },
    )

    private fun safeFileName(fileName: String): String = fileName
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(180)
        .ifBlank { "source.bin" }
}
