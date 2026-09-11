package com.streaming.platform.media

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class MediaWorkerMetricsSnapshot(
    val claimedJobs: Long,
    val completedJobs: Long,
    val failedJobs: Long,
    val cancelledJobs: Long,
    val retryScheduledJobs: Long,
    val heartbeatRenewals: Long,
    val leaseLosses: Long,
    val averageProcessingMillis: Double?,
)

/** In-process observer used by workers and certification runs; export its snapshot to the metrics backend. */
class MediaWorkerMetricsObserver(private val now: () -> Instant = { Clock.System.now() }) : MediaWorkerObserver {
    private val startedAt = mutableMapOf<String, Instant>()
    private var claimedJobs = 0L
    private var completedJobs = 0L
    private var failedJobs = 0L
    private var cancelledJobs = 0L
    private var retryScheduledJobs = 0L
    private var heartbeatRenewals = 0L
    private var leaseLosses = 0L
    private var processingMillis = 0L
    private var measuredJobs = 0L

    @Synchronized
    override fun onClaimed(job: MediaJob) {
        claimedJobs++
        startedAt[job.id] = now()
    }

    @Synchronized
    override fun onHeartbeat(job: MediaJob, renewed: Boolean) {
        if (renewed) heartbeatRenewals++ else leaseLosses++
    }

    @Synchronized
    override fun onCompleted(job: MediaJob) {
        completedJobs++
        recordDuration(job.id)
    }

    @Synchronized
    override fun onCancelled(job: MediaJob) {
        cancelledJobs++
        startedAt.remove(job.id)
    }

    @Synchronized
    override fun onFailed(job: MediaJob, failureCode: String, retryAt: Instant?) {
        failedJobs++
        if (retryAt != null) retryScheduledJobs++
        recordDuration(job.id)
    }

    @Synchronized
    fun snapshot(): MediaWorkerMetricsSnapshot = MediaWorkerMetricsSnapshot(
        claimedJobs = claimedJobs,
        completedJobs = completedJobs,
        failedJobs = failedJobs,
        cancelledJobs = cancelledJobs,
        retryScheduledJobs = retryScheduledJobs,
        heartbeatRenewals = heartbeatRenewals,
        leaseLosses = leaseLosses,
        averageProcessingMillis = if (measuredJobs == 0L) null else processingMillis.toDouble() / measuredJobs,
    )

    private fun recordDuration(jobId: String) {
        val started = startedAt.remove(jobId) ?: return
        processingMillis += (now().toEpochMilliseconds() - started.toEpochMilliseconds()).coerceAtLeast(0)
        measuredJobs++
    }
}
