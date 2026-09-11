package com.streaming.platform.media

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.time.Duration

data class MediaWorkerConfig(
    val workerId: String,
    val maxConcurrentJobs: Int = 2,
    val heartbeatTimeout: Duration = Duration.ofMinutes(2),
    val heartbeatInterval: Duration = Duration.ofSeconds(30),
    val baseRetryDelay: Duration = Duration.ofSeconds(10),
    val maxRetryDelay: Duration = Duration.ofMinutes(10),
)

interface MediaWorkerObserver {
    fun onClaimed(job: MediaJob) {}
    fun onHeartbeat(job: MediaJob, renewed: Boolean) {}
    fun onCompleted(job: MediaJob) {}
    fun onCancelled(job: MediaJob) {}
    fun onFailed(job: MediaJob, failureCode: String, retryAt: Instant?) {}
}

class MediaJobWorker(
    private val queue: MediaJobQueue,
    private val orchestrator: MediaPipelineOrchestrator,
    private val config: MediaWorkerConfig,
    private val now: () -> Instant = { Clock.System.now() },
    private val beforeProcess: suspend (MediaJob) -> Unit = {},
    private val afterProcess: suspend (MediaJob) -> MediaJob = { it },
    private val observer: MediaWorkerObserver = object : MediaWorkerObserver {},
) {
    init {
        require(config.maxConcurrentJobs > 0) { "maxConcurrentJobs must be positive" }
        require(!config.heartbeatTimeout.isNegative && !config.heartbeatTimeout.isZero) { "heartbeatTimeout must be positive" }
        require(!config.heartbeatInterval.isNegative && !config.heartbeatInterval.isZero) { "heartbeatInterval must be positive" }
        require(config.heartbeatInterval < config.heartbeatTimeout) { "heartbeatInterval must be shorter than heartbeatTimeout" }
    }

    suspend fun processAvailable(): List<MediaJob?> = coroutineScope {
        (0 until config.maxConcurrentJobs)
            .map { async { processNext() } }
            .awaitAll()
    }

    suspend fun processNext(): MediaJob? {
        val claimed = queue.claimNext(config.workerId, now()) ?: return null
        observer.onClaimed(claimed)
        if (claimed.cancelRequested) {
            val cancelled = claimed.copy(state = MediaJobState.CANCELLED, workerId = config.workerId, heartbeatAt = now(), updatedAt = now())
            queue.update(cancelled)
            queue.complete(cancelled)
            observer.onCancelled(cancelled)
            return cancelled
        }
        val running = claimed.copy(attempts = claimed.attempts + 1, workerId = config.workerId, heartbeatAt = now(), updatedAt = now())
        queue.update(running)
        return coroutineScope {
            val heartbeatJob = launch {
                while (isActive) {
                    delay(config.heartbeatInterval.toMillis())
                    observer.onHeartbeat(running, queue.heartbeat(running.id, config.workerId, now()))
                }
            }
            try {
                beforeProcess(running)
                val processed = orchestrator.process(running)
                val published = afterProcess(processed)
                val completed = published.copy(progressPercent = 100, workerId = config.workerId, heartbeatAt = now(), updatedAt = now())
                queue.complete(completed)
                observer.onCompleted(completed)
                completed
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val retryAt = if (running.attempts < running.maxAttempts) now().plus(retryDelay(running.attempts)) else null
                val failureCode = error.message ?: "MEDIA_PIPELINE_FAILED"
                // Recording a failure must never itself be able to fail the worker process — e.g.
                // a transient DB error, or (historically) a failureCode too long for its column —
                // otherwise a single bad job takes down every job this worker would ever process.
                try {
                    queue.fail(running, failureCode, retryAt)
                } catch (recordingError: CancellationException) {
                    throw recordingError
                } catch (recordingError: Throwable) {
                    observer.onFailed(running, "FAILURE_NOT_PERSISTED: ${recordingError.message}", retryAt)
                }
                observer.onFailed(running, failureCode, retryAt)
                null
            } finally {
                heartbeatJob.cancelAndJoin()
            }
        }
    }

    private fun retryDelay(attempt: Int): kotlin.time.Duration {
        val exponentialSeconds = config.baseRetryDelay.seconds
            .coerceAtLeast(1)
            .toDouble()
            .let { it * 2.0.pow((attempt - 1).coerceAtLeast(0)) }
            .toLong()
            .coerceAtMost(config.maxRetryDelay.seconds)
        return kotlin.time.Duration.parse("${exponentialSeconds}s")
    }

    private fun Double.pow(exponent: Int): Double = Math.pow(this, exponent.toDouble())
}
