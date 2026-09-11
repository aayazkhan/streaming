package com.streaming.platform.mediaservice

import com.streaming.platform.storage.CompletedUploadPart
import com.streaming.platform.storage.MultipartPartGrant
import com.streaming.platform.storage.MultipartUploadSession
import com.streaming.platform.storage.PresignedObjectGrant
import com.streaming.platform.storage.StorageObjectMetadata
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class MediaUploadStatus { REQUESTED, UPLOADING, UPLOADED, PROCESSING, READY, FAILED }

@Serializable
data class CreateMediaUploadRequest(
    val contentId: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val checksumSha256: String? = null,
)

@Serializable
data class MediaUpload(
    val id: String,
    val contentId: String,
    val objectKey: String,
    val fileName: String,
    val contentType: String,
    val expectedSizeBytes: Long,
    val checksumSha256: String?,
    val status: MediaUploadStatus,
    val createdAt: Instant,
    val completedAt: Instant? = null,
    val multipartUploadId: String? = null,
    val partSizeBytes: Long? = null,
)

@Serializable
data class MediaUploadGrant(
    val upload: MediaUpload,
    val grant: PresignedObjectGrant,
)

@Serializable
data class CompletedMediaUpload(
    val upload: MediaUpload,
    val objectMetadata: StorageObjectMetadata,
    val processingQueued: Boolean,
)

@Serializable
data class StartMultipartUploadRequest(val base: CreateMediaUploadRequest, val partSizeBytes: Long = 64L * 1024 * 1024)

@Serializable
data class MultipartUploadGrant(
    val upload: MediaUpload,
    val session: MultipartUploadSession,
)

@Serializable
data class CompleteMultipartUploadRequest(val parts: List<CompletedUploadPart>)

@Serializable
data class CompletedMultipartUpload(
    val upload: MediaUpload,
    val objectMetadata: StorageObjectMetadata,
    val processingQueued: Boolean,
)
