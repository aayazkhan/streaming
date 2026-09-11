package com.streaming.platform.playback

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackQoeMetrics(
    val startupTimeMillis: Long?,
    val totalRebufferMillis: Long,
    val rebufferRatio: Double,
    val watchDurationSeconds: Long,
    val averageBitrateKbps: Double?,
    val seekCount: Int,
    val qualityChangeCount: Int,
    val audioChangeCount: Int,
    val subtitleChangeCount: Int,
    val playbackErrorCount: Int,
    val playbackFailureRate: Double,
    val completed: Boolean,
)

/** Deterministic session-level QoE calculator for clients, tests, and downstream aggregation jobs. */
class PlaybackQoeAggregator {
    private val events = mutableListOf<PlaybackTelemetryEvent>()

    fun record(event: PlaybackTelemetryEvent): PlaybackQoeMetrics {
        events += event
        return snapshot()
    }

    fun snapshot(): PlaybackQoeMetrics = calculate(events)

    companion object {
        fun calculate(events: List<PlaybackTelemetryEvent>): PlaybackQoeMetrics {
            val ordered = events.sortedBy { it.occurredAt.toEpochMilliseconds() }
            val start = ordered.firstOrNull { it.type == PlaybackEventType.START }
            val firstFrame = ordered.firstOrNull { it.type == PlaybackEventType.FIRST_FRAME }
            var bufferStartedAt: Long? = null
            var totalRebufferMillis = 0L
            ordered.forEach { event ->
                val at = event.occurredAt.toEpochMilliseconds()
                when (event.type) {
                    PlaybackEventType.BUFFER_START, PlaybackEventType.BUFFERING -> if (bufferStartedAt == null) bufferStartedAt = at
                    PlaybackEventType.BUFFER_END -> {
                        bufferStartedAt?.let { totalRebufferMillis += (at - it).coerceAtLeast(0) }
                        bufferStartedAt = null
                    }
                    else -> Unit
                }
            }
            val watchDurationSeconds = ordered.maxOfOrNull { it.positionSeconds ?: 0L }?.coerceAtLeast(0) ?: 0
            val bitrateSamples = ordered.mapNotNull { it.metadata["bitrate_kbps"]?.toDoubleOrNull() }
            val starts = ordered.count { it.type == PlaybackEventType.START }
            val errors = ordered.count { it.type == PlaybackEventType.ERROR }
            val startupTimeMillis = if (start != null && firstFrame != null) {
                (firstFrame.occurredAt.toEpochMilliseconds() - start.occurredAt.toEpochMilliseconds()).coerceAtLeast(0)
            } else {
                null
            }
            val playbackMillis = watchDurationSeconds * 1_000L
            return PlaybackQoeMetrics(
                startupTimeMillis = startupTimeMillis,
                totalRebufferMillis = totalRebufferMillis,
                rebufferRatio = if (playbackMillis > 0) totalRebufferMillis.toDouble() / playbackMillis else 0.0,
                watchDurationSeconds = watchDurationSeconds,
                averageBitrateKbps = bitrateSamples.takeIf { it.isNotEmpty() }?.average(),
                seekCount = ordered.count { it.type == PlaybackEventType.SEEK },
                qualityChangeCount = ordered.count { it.type == PlaybackEventType.QUALITY_CHANGED },
                audioChangeCount = ordered.count { it.type == PlaybackEventType.AUDIO_CHANGED },
                subtitleChangeCount = ordered.count { it.type == PlaybackEventType.SUBTITLE_CHANGED },
                playbackErrorCount = errors,
                playbackFailureRate = if (starts > 0) (errors.toDouble() / starts).coerceIn(0.0, 1.0) else 0.0,
                completed = ordered.any { it.type == PlaybackEventType.COMPLETE },
            )
        }
    }
}
