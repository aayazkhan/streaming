package com.streaming.platform.media

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class MediaJobWorkerTest {
    @Test
    fun workerMetricsCaptureRetriesHeartbeatsLeaseLossAndDuration() {
        val initial = Clock.System.now()
        var current = initial
        val observer = MediaWorkerMetricsObserver { current }
        val job = testJob(initial)

        observer.onClaimed(job)
        current = initial + 1.seconds
        observer.onHeartbeat(job, renewed = true)
        observer.onHeartbeat(job, renewed = false)
        observer.onFailed(job, "TEMPORARY_FAILURE", initial + 30.seconds)

        val metrics = observer.snapshot()
        assertEquals(1, metrics.claimedJobs)
        assertEquals(1, metrics.failedJobs)
        assertEquals(1, metrics.retryScheduledJobs)
        assertEquals(1, metrics.heartbeatRenewals)
        assertEquals(1, metrics.leaseLosses)
        assertEquals(1_000.0, metrics.averageProcessingMillis)
    }

    @Test
    fun failedJobIsRescheduledWithRetryMetadata() = runBlocking {
        val now = Clock.System.now()
        val job = testJob(now)
        var stored = job
        var retryAt: Instant? = null
        val queue = object : MediaJobQueue {
            override suspend fun save(job: MediaJob) { stored = job }
            override suspend fun update(job: MediaJob) { stored = job }
            override suspend fun find(jobId: String) = stored.takeIf { it.id == jobId }
            override suspend fun claimNext(workerId: String, now: Instant) = stored.takeIf { it.state == MediaJobState.ACCEPTED }
            override suspend fun heartbeat(jobId: String, workerId: String, at: Instant) = true
            override suspend fun requestCancellation(jobId: String) = true
            override suspend fun complete(job: MediaJob) { stored = job }
            override suspend fun fail(job: MediaJob, failureCode: String, retryAtValue: Instant?) {
                retryAt = retryAtValue
                stored = job.copy(state = if (retryAtValue == null) MediaJobState.FAILED else MediaJobState.ACCEPTED, failureCode = failureCode)
            }
        }
        val orchestrator = MediaPipelineOrchestrator(
            storage = object : MediaObjectStorage { override suspend fun exists(key: String) = true; override suspend fun put(key: String, bytes: ByteArray, contentType: String) = Unit; override suspend fun delete(key: String) = Unit },
            transcoder = object : VideoTranscoder { override suspend fun transcode(sourceKey: String, outputPrefix: String, profiles: List<TranscodeProfile>): String = error("ENCODER_DOWN") },
            packager = object : AdaptiveBitratePackager { override suspend fun packageHlsAndDash(transcodeOutputPrefix: String, outputPrefix: String) = PackagedMedia(null, null) },
            thumbnails = object : ThumbnailGenerator { override suspend fun generate(sourceKey: String, outputPrefix: String) = GeneratedThumbnails(null, null) },
            jobs = queue,
        )

        MediaJobWorker(queue, orchestrator, MediaWorkerConfig("worker-1", baseRetryDelay = Duration.ofSeconds(1)), now = { now }).processNext()

        assertEquals(MediaJobState.ACCEPTED, stored.state)
        assertEquals(1, stored.attempts)
        assertEquals("ENCODER_DOWN", stored.failureCode)
        assertNotNull(retryAt)
    }

    private fun testJob(now: Instant) = MediaJob(
        id = "00000000-0000-0000-0000-000000000001",
        contentId = "00000000-0000-0000-0000-000000000002",
        sourceKey = "source.mp4",
        state = MediaJobState.ACCEPTED,
        profiles = listOf(TranscodeProfile("360p", 640, 360, 800)),
        createdAt = now,
        updatedAt = now,
        idempotencyKey = "test-job",
    )
}
