package com.streaming.platform.playback

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackQoeTest {
    @Test
    fun calculatesStartupRebufferQualityAndFailureMetrics() {
        val events = listOf(
            event(PlaybackEventType.START, 0, 1_000),
            event(PlaybackEventType.FIRST_FRAME, 0, 2_250),
            event(PlaybackEventType.BUFFER_START, 10, 4_000),
            event(PlaybackEventType.BUFFER_END, 10, 5_000),
            event(PlaybackEventType.QUALITY_CHANGED, 12, 6_000, mapOf("bitrate_kbps" to "2000")),
            event(PlaybackEventType.HEARTBEAT, 30, 7_000, mapOf("bitrate_kbps" to "4000")),
            event(PlaybackEventType.ERROR, 30, 8_000),
            event(PlaybackEventType.COMPLETE, 30, 9_000),
        )

        val metrics = PlaybackQoeAggregator.calculate(events)

        assertEquals(1_250, metrics.startupTimeMillis)
        assertEquals(1_000, metrics.totalRebufferMillis)
        assertEquals(1.0 / 30.0, metrics.rebufferRatio)
        assertEquals(30, metrics.watchDurationSeconds)
        assertEquals(3_000.0, metrics.averageBitrateKbps)
        assertEquals(1, metrics.qualityChangeCount)
        assertEquals(1, metrics.playbackErrorCount)
        assertEquals(1.0, metrics.playbackFailureRate)
        assertTrue(metrics.completed)
    }

    private fun event(type: PlaybackEventType, position: Long, millis: Long, metadata: Map<String, String> = emptyMap()) =
        PlaybackTelemetryEvent("session-1", type, position, occurredAt = Instant.fromEpochMilliseconds(millis), metadata = metadata)
}
