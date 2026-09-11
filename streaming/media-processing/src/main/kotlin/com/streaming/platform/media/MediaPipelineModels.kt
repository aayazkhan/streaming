package com.streaming.platform.media

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class MediaJobState { ACCEPTED, UPLOADING, TRANSCODING, PACKAGING, SUBTITLES, THUMBNAILS, READY, FAILED, CANCELLED }

@Serializable
data class TranscodeProfile(
    val name: String,
    val width: Int,
    val height: Int,
    val bitrateKbps: Int,
    val codec: String = "h264",
)

@Serializable
data class MediaJob(
    val id: String,
    val contentId: String,
    val sourceKey: String,
    val state: MediaJobState,
    val profiles: List<TranscodeProfile>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val failureCode: String? = null,
    val hlsManifestKey: String? = null,
    val dashManifestKey: String? = null,
    val posterKey: String? = null,
    val previewKey: String? = null,
    val subtitleKeys: Map<String, String> = emptyMap(),
    val idempotencyKey: String? = null,
    val attempts: Int = 0,
    val maxAttempts: Int = 3,
    val progressPercent: Int = 0,
    val cancelRequested: Boolean = false,
    val workerId: String? = null,
    val heartbeatAt: Instant? = null,
    val nextAttemptAt: Instant? = null,
)

data class PackagedMedia(
    val hlsManifestKey: String?,
    val dashManifestKey: String?,
)

data class GeneratedThumbnails(
    val posterKey: String?,
    val previewKey: String?,
)

data class GeneratedSubtitles(val tracks: Map<String, String>)

interface MediaObjectStorage {
    suspend fun exists(key: String): Boolean
    suspend fun put(key: String, bytes: ByteArray, contentType: String)
    suspend fun delete(key: String)
}

interface VideoTranscoder {
    suspend fun transcode(sourceKey: String, outputPrefix: String, profiles: List<TranscodeProfile>): String
}

interface AdaptiveBitratePackager {
    suspend fun packageHlsAndDash(transcodeOutputPrefix: String, outputPrefix: String): PackagedMedia
}

interface ThumbnailGenerator {
    suspend fun generate(sourceKey: String, outputPrefix: String): GeneratedThumbnails
}

fun interface SubtitleProcessor {
    suspend fun process(sourceKey: String, outputPrefix: String): GeneratedSubtitles
}

interface MediaJobStore {
    suspend fun save(job: MediaJob)
    suspend fun update(job: MediaJob)
}

interface MediaJobQueue : MediaJobStore {
    suspend fun find(jobId: String): MediaJob?
    suspend fun claimNext(workerId: String, now: Instant): MediaJob?
    suspend fun heartbeat(jobId: String, workerId: String, at: Instant): Boolean
    suspend fun requestCancellation(jobId: String): Boolean
    suspend fun complete(job: MediaJob)
    suspend fun fail(job: MediaJob, failureCode: String, retryAt: Instant?)
}

class MediaPipelineOrchestrator(
    private val storage: MediaObjectStorage,
    private val transcoder: VideoTranscoder,
    private val packager: AdaptiveBitratePackager,
    private val thumbnails: ThumbnailGenerator,
    private val jobs: MediaJobStore,
    private val subtitles: SubtitleProcessor = SubtitleProcessor { _, _ -> GeneratedSubtitles(emptyMap()) },
) {
    suspend fun process(job: MediaJob): MediaJob {
        var current = job.copy(state = MediaJobState.UPLOADING, progressPercent = 5, updatedAt = kotlinx.datetime.Clock.System.now())
        jobs.save(current)
        try {
            check(storage.exists(current.sourceKey)) { "MEDIA_SOURCE_NOT_FOUND" }
            current = current.copy(state = MediaJobState.TRANSCODING, progressPercent = 20, updatedAt = kotlinx.datetime.Clock.System.now())
            jobs.update(current)
            val outputPrefix = "media/${current.contentId}/${current.id}"
            val transcodePrefix = transcoder.transcode(current.sourceKey, outputPrefix, current.profiles)
            current = current.copy(state = MediaJobState.PACKAGING, progressPercent = 65, updatedAt = kotlinx.datetime.Clock.System.now())
            jobs.update(current)
            val packaged = packager.packageHlsAndDash(transcodePrefix, outputPrefix)
            current = current.copy(
                hlsManifestKey = packaged.hlsManifestKey,
                dashManifestKey = packaged.dashManifestKey,
            )
            current = current.copy(state = MediaJobState.SUBTITLES, progressPercent = 80, updatedAt = kotlinx.datetime.Clock.System.now())
            jobs.update(current)
            val processedSubtitles = subtitles.process(current.sourceKey, outputPrefix)
            current = current.copy(subtitleKeys = processedSubtitles.tracks)
            current = current.copy(state = MediaJobState.THUMBNAILS, progressPercent = 90, updatedAt = kotlinx.datetime.Clock.System.now())
            jobs.update(current)
            val generated = thumbnails.generate(current.sourceKey, outputPrefix)
            current = current.copy(state = MediaJobState.READY, progressPercent = 100, updatedAt = kotlinx.datetime.Clock.System.now())
                .copy(posterKey = generated.posterKey, previewKey = generated.previewKey)
            jobs.update(current)
            return current
        } catch (error: Throwable) {
            val failed = current.copy(
                state = MediaJobState.FAILED,
                failureCode = error.message ?: "MEDIA_PIPELINE_FAILED",
                updatedAt = kotlinx.datetime.Clock.System.now(),
            )
            jobs.update(failed)
            throw error
        }
    }
}
