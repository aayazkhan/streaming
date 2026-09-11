package com.streaming.platform.storage

import io.minio.GetPresignedObjectUrlArgs
import io.minio.MinioClient
import io.minio.DownloadObjectArgs
import io.minio.UploadObjectArgs
import io.minio.StatObjectArgs
import io.minio.errors.ErrorResponseException
import io.minio.http.Method
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import java.time.Duration
import java.nio.file.Path
import java.net.URI
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.ChecksumAlgorithm
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload
import software.amazon.awssdk.services.s3.model.CompletedPart
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.UploadPartRequest
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest

@Serializable
data class StorageObjectMetadata(
    val key: String,
    val sizeBytes: Long,
    val contentType: String?,
    val etag: String?,
)

@Serializable
data class PresignedObjectGrant(
    val key: String,
    val method: String,
    val url: String,
    val expiresAt: Instant,
    val requiredHeaders: Map<String, String> = emptyMap(),
)

interface ObjectStorageProvider {
    suspend fun createUploadGrant(key: String, contentType: String, expiresIn: Duration): PresignedObjectGrant
    suspend fun createDownloadGrant(key: String, expiresIn: Duration): PresignedObjectGrant
    suspend fun stat(key: String): StorageObjectMetadata?
    suspend fun downloadTo(key: String, destination: Path)
    suspend fun uploadFile(key: String, source: Path, contentType: String)
}

@Serializable
data class MultipartUploadSession(
    val uploadId: String,
    val key: String,
    val partSizeBytes: Long,
    val expiresAt: Instant,
)

@Serializable
data class MultipartPartGrant(
    val partNumber: Int,
    val grant: PresignedObjectGrant,
)

@Serializable
data class CompletedUploadPart(
    val partNumber: Int,
    val etag: String,
    val checksumSha256: String? = null,
)

interface MultipartObjectStorageProvider {
    suspend fun initiateMultipartUpload(key: String, contentType: String, expiresIn: Duration, partSizeBytes: Long): MultipartUploadSession
    suspend fun createPartUploadGrant(key: String, uploadId: String, partNumber: Int, expiresIn: Duration, checksumSha256: String? = null): MultipartPartGrant
    suspend fun completeMultipartUpload(key: String, uploadId: String, parts: List<CompletedUploadPart>): StorageObjectMetadata?
    suspend fun abortMultipartUpload(key: String, uploadId: String)
}

data class S3CompatibleStorageConfig(
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,
    val region: String = "us-east-1",
    // Presigned URLs are handed to a client (browser, curl) that is not on the same network as
    // the server issuing them — in Docker Compose/Kubernetes, `endpoint` is typically an internal
    // service DNS name (e.g. "http://minio:9000") the client can't resolve. `publicEndpoint` lets
    // presigned-URL generation use a client-reachable host while internal calls (stat/headObject)
    // keep using `endpoint`. Falls back to `endpoint` when unset, matching prior behavior.
    val publicEndpoint: String = endpoint,
)

/**
 * S3-compatible storage adapter. The API only issues short-lived URLs; media bytes do not pass through Ktor.
 */
class S3CompatibleObjectStorage private constructor(
    private val config: S3CompatibleStorageConfig,
    private val client: MinioClient,
    private val presignClient: MinioClient,
) : ObjectStorageProvider {
    constructor(config: S3CompatibleStorageConfig) : this(
        config,
        MinioClient.builder()
            .endpoint(config.endpoint)
            .credentials(config.accessKey, config.secretKey)
            .region(config.region)
            .build(),
        MinioClient.builder()
            .endpoint(config.publicEndpoint)
            .credentials(config.accessKey, config.secretKey)
            .region(config.region)
            .build(),
    )
    override suspend fun createUploadGrant(key: String, contentType: String, expiresIn: Duration): PresignedObjectGrant =
        presign(Method.PUT, key, expiresIn, mapOf("Content-Type" to contentType))

    override suspend fun createDownloadGrant(key: String, expiresIn: Duration): PresignedObjectGrant =
        presign(Method.GET, key, expiresIn)

    override suspend fun stat(key: String): StorageObjectMetadata? = withContext(Dispatchers.IO) {
        try {
            val result = client.statObject(
                StatObjectArgs.builder().bucket(config.bucket).`object`(key).build(),
            )
            StorageObjectMetadata(key, result.size(), result.contentType(), result.etag())
        } catch (_: ErrorResponseException) {
            null
        }
    }

    override suspend fun downloadTo(key: String, destination: Path) = withContext(Dispatchers.IO) {
        java.nio.file.Files.createDirectories(destination.parent)
        // MinIO's downloadObject refuses to overwrite an existing file — without this, a job retry
        // after any failure past the download step (e.g. a transcode error) would fail every
        // subsequent attempt with "Destination file already exists" instead of actually retrying.
        java.nio.file.Files.deleteIfExists(destination)
        client.downloadObject(
            DownloadObjectArgs.builder().bucket(config.bucket).`object`(key).filename(destination.toString()).build(),
        )
    }

    override suspend fun uploadFile(key: String, source: Path, contentType: String) = withContext(Dispatchers.IO) {
        client.uploadObject(
            UploadObjectArgs.builder().bucket(config.bucket).`object`(key).filename(source.toString()).contentType(contentType).build(),
        )
        Unit
    }

    private suspend fun presign(method: Method, key: String, expiresIn: Duration, requiredHeaders: Map<String, String> = emptyMap()): PresignedObjectGrant = withContext(Dispatchers.IO) {
        require(!key.contains("..")) { "Storage key must not contain parent traversal" }
        require(!expiresIn.isNegative && !expiresIn.isZero) { "Storage grant expiry must be positive" }
        val seconds = expiresIn.seconds.coerceIn(1, 7 * 24 * 60 * 60).toInt()
        val url = presignClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(method)
                .bucket(config.bucket)
                .`object`(key)
                .expiry(seconds)
                .build(),
        )
        PresignedObjectGrant(key, method.name, url, Clock.System.now().plus(kotlin.time.Duration.parse("${seconds}s")), requiredHeaders)
    }
}

/** AWS SigV4 multipart implementation compatible with S3, MinIO, and other S3-compatible providers. */
class S3CompatibleMultipartStorage private constructor(
    private val config: S3CompatibleStorageConfig,
    private val client: S3Client,
    private val presigner: S3Presigner,
) : MultipartObjectStorageProvider, AutoCloseable {
    constructor(config: S3CompatibleStorageConfig) : this(
        config,
        S3Client.builder()
            .endpointOverride(URI.create(config.endpoint))
            .region(Region.of(config.region))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(config.accessKey, config.secretKey)))
            .forcePathStyle(true)
            .build(),
        S3Presigner.builder()
            .endpointOverride(URI.create(config.publicEndpoint))
            .region(Region.of(config.region))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(config.accessKey, config.secretKey)))
            .build(),
    )

    override suspend fun initiateMultipartUpload(key: String, contentType: String, expiresIn: Duration, partSizeBytes: Long): MultipartUploadSession = withContext(Dispatchers.IO) {
        require(partSizeBytes in 5L * 1024 * 1024..5L * 1024 * 1024 * 1024) { "Multipart part size must be between 5 MiB and 5 GiB" }
        val response = client.createMultipartUpload(
            CreateMultipartUploadRequest.builder()
                .bucket(config.bucket)
                .key(key)
                .contentType(contentType)
                .checksumAlgorithm(ChecksumAlgorithm.SHA256)
                .build(),
        )
        MultipartUploadSession(response.uploadId(), key, partSizeBytes, Clock.System.now().plus(kotlin.time.Duration.parse("${expiresIn.seconds}s")))
    }

    override suspend fun createPartUploadGrant(key: String, uploadId: String, partNumber: Int, expiresIn: Duration, checksumSha256: String?): MultipartPartGrant = withContext(Dispatchers.IO) {
        require(partNumber in 1..10_000) { "Multipart part number must be between 1 and 10,000" }
        val requestBuilder = UploadPartRequest.builder().bucket(config.bucket).key(key).uploadId(uploadId).partNumber(partNumber)
        checksumSha256?.let { requestBuilder.checksumSHA256(it) }
        val request = requestBuilder.build()
        val presigned = presigner.presignUploadPart(
            UploadPartPresignRequest.builder().signatureDuration(expiresIn).uploadPartRequest(request).build(),
        )
        val requiredHeaders = checksumSha256?.let { mapOf("x-amz-checksum-sha256" to it) } ?: emptyMap()
        MultipartPartGrant(partNumber, PresignedObjectGrant(key, "PUT", presigned.url().toString(), Clock.System.now().plus(kotlin.time.Duration.parse("${expiresIn.seconds}s")), requiredHeaders))
    }

    override suspend fun completeMultipartUpload(key: String, uploadId: String, parts: List<CompletedUploadPart>): StorageObjectMetadata? = withContext(Dispatchers.IO) {
        require(parts.isNotEmpty() && parts.map { it.partNumber }.distinct().size == parts.size) { "Multipart completion requires unique parts" }
        val completed = parts.sortedBy { it.partNumber }.map {
            CompletedPart.builder().partNumber(it.partNumber).eTag(it.etag).checksumSHA256(it.checksumSha256).build()
        }
        client.completeMultipartUpload(
            CompleteMultipartUploadRequest.builder()
                .bucket(config.bucket).key(key).uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completed).build())
                .build(),
        )
        try {
            val result = client.headObject(HeadObjectRequest.builder().bucket(config.bucket).key(key).build())
            StorageObjectMetadata(key, result.contentLength(), result.contentType(), result.eTag())
        } catch (_: S3Exception) {
            null
        }
    }

    override suspend fun abortMultipartUpload(key: String, uploadId: String) = withContext(Dispatchers.IO) {
        client.abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(config.bucket).key(key).uploadId(uploadId).build())
        Unit
    }

    override fun close() {
        client.close()
        presigner.close()
    }
}
