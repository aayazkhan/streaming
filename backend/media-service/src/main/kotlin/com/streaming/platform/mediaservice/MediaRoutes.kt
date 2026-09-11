package com.streaming.platform.mediaservice

import com.streaming.platform.storage.MultipartObjectStorageProvider
import com.streaming.platform.storage.ObjectStorageProvider
import com.streaming.platform.media.MediaJob
import com.streaming.platform.media.MediaJobQueue
import com.streaming.platform.media.MediaJobState
import com.streaming.platform.media.TranscodeProfile
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.Clock
import java.time.Duration

class MediaException(val status: HttpStatusCode, val code: String, override val message: String) : RuntimeException(message)

private fun JWTPrincipal.requireAdmin() {
    if (payload.getClaim("role").asString() != "ADMIN") {
        throw MediaException(HttpStatusCode.Forbidden, "MEDIA_ADMIN_REQUIRED", "Media ingestion requires an administrator role")
    }
}

private fun validateUploadRequest(request: CreateMediaUploadRequest) {
    if (request.sizeBytes <= 0 || request.sizeBytes > 50L * 1024 * 1024 * 1024) {
        throw MediaException(HttpStatusCode.BadRequest, "INVALID_MEDIA_SIZE", "Media size must be between 1 byte and 50 GiB")
    }
    if (!request.contentType.startsWith("video/")) {
        throw MediaException(HttpStatusCode.BadRequest, "INVALID_MEDIA_TYPE", "Only video uploads are accepted")
    }
    if (request.fileName.isBlank() || request.fileName.length > 255) {
        throw MediaException(HttpStatusCode.BadRequest, "INVALID_FILE_NAME", "A file name between 1 and 255 characters is required")
    }
    if (request.checksumSha256 != null && !request.checksumSha256.matches(Regex("[A-Fa-f0-9]{64}"))) {
        throw MediaException(HttpStatusCode.BadRequest, "INVALID_CHECKSUM", "checksumSha256 must be a SHA-256 hexadecimal digest")
    }
}

fun Route.mediaRoutes(
    repository: MediaRepository,
    storage: ObjectStorageProvider,
    multipartStorage: MultipartObjectStorageProvider? = null,
    jobQueue: MediaJobQueue? = null,
    grantTtl: Duration = Duration.ofMinutes(15),
) {
    route("/media") {
        post("/uploads") {
            call.principal<JWTPrincipal>()?.requireAdmin() ?: throw MediaException(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "Authentication is required")
            val request = call.receive<CreateMediaUploadRequest>()
            validateUploadRequest(request)
            val upload = try { repository.createUpload(request) } catch (error: IllegalArgumentException) {
                throw MediaException(HttpStatusCode.NotFound, "CONTENT_NOT_FOUND", "Content does not exist")
            } catch (error: IllegalStateException) {
                throw MediaException(HttpStatusCode.Conflict, error.message ?: "Upload cannot be created", "Upload cannot be created")
            }
            call.respond(MediaUploadGrant(upload, storage.createUploadGrant(upload.objectKey, upload.contentType, grantTtl)))
        }
        post("/uploads/multipart") {
            call.principal<JWTPrincipal>()?.requireAdmin() ?: throw MediaException(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "Authentication is required")
            val provider = multipartStorage ?: throw MediaException(HttpStatusCode.NotImplemented, "MULTIPART_STORAGE_UNAVAILABLE", "Multipart storage is not configured")
            val request = call.receive<StartMultipartUploadRequest>()
            validateUploadRequest(request.base)
            val upload = try { repository.createUpload(request.base) } catch (error: IllegalArgumentException) {
                throw MediaException(HttpStatusCode.NotFound, "CONTENT_NOT_FOUND", "Content does not exist")
            } catch (error: IllegalStateException) {
                throw MediaException(HttpStatusCode.Conflict, error.message ?: "Upload cannot be created", "Upload cannot be created")
            }
            val session = provider.initiateMultipartUpload(upload.objectKey, upload.contentType, grantTtl, request.partSizeBytes)
            call.respond(MultipartUploadGrant(repository.attachMultipartSession(upload.id, session), session))
        }
        post("/uploads/{uploadId}/parts/{partNumber}") {
            call.principal<JWTPrincipal>()?.requireAdmin() ?: throw MediaException(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "Authentication is required")
            val provider = multipartStorage ?: throw MediaException(HttpStatusCode.NotImplemented, "MULTIPART_STORAGE_UNAVAILABLE", "Multipart storage is not configured")
            val uploadId = call.parameters["uploadId"] ?: throw MediaException(HttpStatusCode.BadRequest, "MISSING_UPLOAD_ID", "Upload id is required")
            val partNumber = call.parameters["partNumber"]?.toIntOrNull() ?: throw MediaException(HttpStatusCode.BadRequest, "INVALID_PART_NUMBER", "Part number is invalid")
            val upload = repository.findUpload(uploadId) ?: throw MediaException(HttpStatusCode.NotFound, "UPLOAD_NOT_FOUND", "Upload does not exist")
            val multipartId = upload.multipartUploadId ?: throw MediaException(HttpStatusCode.Conflict, "NOT_MULTIPART_UPLOAD", "Upload is not multipart")
            val checksumSha256 = call.request.headers["X-Upload-Part-Checksum-Sha256"]
            if (checksumSha256 != null && !checksumSha256.matches(Regex("[A-Za-z0-9+/]{43}="))) {
                throw MediaException(HttpStatusCode.BadRequest, "INVALID_PART_CHECKSUM", "Part checksum must be a base64 SHA-256 digest")
            }
            call.respond(provider.createPartUploadGrant(upload.objectKey, multipartId, partNumber, grantTtl, checksumSha256))
        }
        post("/uploads/{uploadId}/complete-multipart") {
            call.principal<JWTPrincipal>()?.requireAdmin() ?: throw MediaException(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "Authentication is required")
            val provider = multipartStorage ?: throw MediaException(HttpStatusCode.NotImplemented, "MULTIPART_STORAGE_UNAVAILABLE", "Multipart storage is not configured")
            val uploadId = call.parameters["uploadId"] ?: throw MediaException(HttpStatusCode.BadRequest, "MISSING_UPLOAD_ID", "Upload id is required")
            val upload = repository.findUpload(uploadId) ?: throw MediaException(HttpStatusCode.NotFound, "UPLOAD_NOT_FOUND", "Upload does not exist")
            val multipartId = upload.multipartUploadId ?: throw MediaException(HttpStatusCode.Conflict, "NOT_MULTIPART_UPLOAD", "Upload is not multipart")
            val request = call.receive<CompleteMultipartUploadRequest>()
            if (request.parts.isEmpty() || request.parts.any { it.partNumber !in 1..10_000 || it.etag.isBlank() }) {
                throw MediaException(HttpStatusCode.BadRequest, "INVALID_MULTIPART_PARTS", "Part numbers and ETags are required")
            }
            val objectMetadata = provider.completeMultipartUpload(upload.objectKey, multipartId, request.parts)
                ?: throw MediaException(HttpStatusCode.Conflict, "MEDIA_NOT_AVAILABLE", "Completed object is not available")
            if (objectMetadata.sizeBytes != upload.expectedSizeBytes) {
                throw MediaException(HttpStatusCode.UnprocessableEntity, "MEDIA_SIZE_MISMATCH", "Uploaded object size does not match the declared size")
            }
            val completed = repository.markUploaded(uploadId, Clock.System.now())
            val queued = enqueue(completed, jobQueue)
            call.respond(CompletedMultipartUpload(completed, objectMetadata, processingQueued = queued))
        }
        post("/uploads/{uploadId}/abort") {
            call.principal<JWTPrincipal>()?.requireAdmin() ?: throw MediaException(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "Authentication is required")
            val provider = multipartStorage ?: throw MediaException(HttpStatusCode.NotImplemented, "MULTIPART_STORAGE_UNAVAILABLE", "Multipart storage is not configured")
            val uploadId = call.parameters["uploadId"] ?: throw MediaException(HttpStatusCode.BadRequest, "MISSING_UPLOAD_ID", "Upload id is required")
            val upload = repository.findUpload(uploadId) ?: throw MediaException(HttpStatusCode.NotFound, "UPLOAD_NOT_FOUND", "Upload does not exist")
            val multipartId = upload.multipartUploadId ?: throw MediaException(HttpStatusCode.Conflict, "NOT_MULTIPART_UPLOAD", "Upload is not multipart")
            provider.abortMultipartUpload(upload.objectKey, multipartId)
            call.respond(repository.markAborted(uploadId))
        }
        post("/uploads/{uploadId}/complete") {
            call.principal<JWTPrincipal>()?.requireAdmin() ?: throw MediaException(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "Authentication is required")
            val uploadId = call.parameters["uploadId"] ?: throw MediaException(HttpStatusCode.BadRequest, "MISSING_UPLOAD_ID", "Upload id is required")
            val upload = repository.findUpload(uploadId) ?: throw MediaException(HttpStatusCode.NotFound, "UPLOAD_NOT_FOUND", "Upload does not exist")
            val objectMetadata = storage.stat(upload.objectKey) ?: throw MediaException(HttpStatusCode.Conflict, "MEDIA_NOT_UPLOADED", "The object has not arrived in storage")
            if (objectMetadata.sizeBytes != upload.expectedSizeBytes) {
                throw MediaException(HttpStatusCode.UnprocessableEntity, "MEDIA_SIZE_MISMATCH", "Uploaded object size does not match the declared size")
            }
            val completed = repository.markUploaded(uploadId, Clock.System.now())
            val queued = enqueue(completed, jobQueue)
            call.respond(CompletedMediaUpload(completed, objectMetadata, processingQueued = queued))
        }
        get("/uploads/{uploadId}") {
            call.principal<JWTPrincipal>()?.requireAdmin() ?: throw MediaException(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "Authentication is required")
            val uploadId = call.parameters["uploadId"] ?: throw MediaException(HttpStatusCode.BadRequest, "MISSING_UPLOAD_ID", "Upload id is required")
            call.respond(repository.findUpload(uploadId) ?: throw MediaException(HttpStatusCode.NotFound, "UPLOAD_NOT_FOUND", "Upload does not exist"))
        }
        get("/uploads/{uploadId}/download") {
            call.principal<JWTPrincipal>()?.requireAdmin() ?: throw MediaException(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "Authentication is required")
            val uploadId = call.parameters["uploadId"] ?: throw MediaException(HttpStatusCode.BadRequest, "MISSING_UPLOAD_ID", "Upload id is required")
            val upload = repository.findUpload(uploadId) ?: throw MediaException(HttpStatusCode.NotFound, "UPLOAD_NOT_FOUND", "Upload does not exist")
            call.respond(storage.createDownloadGrant(upload.objectKey, grantTtl))
        }
        get("/jobs/{jobId}") {
            call.principal<JWTPrincipal>()?.requireAdmin() ?: throw MediaException(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "Authentication is required")
            val queue = jobQueue ?: throw MediaException(HttpStatusCode.NotImplemented, "MEDIA_QUEUE_UNAVAILABLE", "Media queue is not configured")
            val jobId = call.parameters["jobId"] ?: throw MediaException(HttpStatusCode.BadRequest, "MISSING_JOB_ID", "Job id is required")
            call.respond(queue.find(jobId) ?: throw MediaException(HttpStatusCode.NotFound, "JOB_NOT_FOUND", "Media job does not exist"))
        }
        post("/jobs/{jobId}/cancel") {
            call.principal<JWTPrincipal>()?.requireAdmin() ?: throw MediaException(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "Authentication is required")
            val queue = jobQueue ?: throw MediaException(HttpStatusCode.NotImplemented, "MEDIA_QUEUE_UNAVAILABLE", "Media queue is not configured")
            val jobId = call.parameters["jobId"] ?: throw MediaException(HttpStatusCode.BadRequest, "MISSING_JOB_ID", "Job id is required")
            if (!queue.requestCancellation(jobId)) throw MediaException(HttpStatusCode.Conflict, "JOB_NOT_CANCELLABLE", "Media job is already terminal or does not exist")
            call.respond(mapOf("jobId" to jobId, "cancelRequested" to true))
        }
    }
}

private suspend fun enqueue(upload: MediaUpload, queue: MediaJobQueue?): Boolean {
    if (queue == null) return false
    val now = Clock.System.now()
    queue.save(
        MediaJob(
            id = upload.id,
            contentId = upload.contentId,
            sourceKey = upload.objectKey,
            state = MediaJobState.ACCEPTED,
            profiles = listOf(
                TranscodeProfile("240p", 426, 240, 400),
                TranscodeProfile("360p", 640, 360, 800),
                TranscodeProfile("480p", 854, 480, 1_200),
                TranscodeProfile("720p", 1_280, 720, 2_500),
                TranscodeProfile("1080p", 1_920, 1_080, 5_000),
            ),
            createdAt = now,
            updatedAt = now,
            idempotencyKey = "media-upload:${upload.id}",
        ),
    )
    return true
}
