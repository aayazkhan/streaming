package com.streaming.platform.playback

import kotlinx.serialization.Serializable

@Serializable
enum class PlaybackFailurePoint {
    CDN_MANIFEST,
    CDN_SEGMENT,
    ORIGIN,
    PLAYBACK_TOKEN,
    DRM_LICENSE,
    DRM_RENEWAL,
    NETWORK_OFFLINE,
    DOWNLOAD_INTERRUPTED,
    DATABASE,
    WORKER_CRASH,
}

class PlaybackFailureInjectionException(val point: PlaybackFailurePoint) :
    IllegalStateException("Injected playback failure at ${point.name}")

fun interface PlaybackFailureInjector {
    fun check(point: PlaybackFailurePoint): Result<Unit>
}

object NoopPlaybackFailureInjector : PlaybackFailureInjector {
    override fun check(point: PlaybackFailurePoint): Result<Unit> = Result.success(Unit)
}

/** Test-only fault plan that can be toggled around native, CDN, and DRM adapter calls. */
class ScriptedPlaybackFailureInjector(initial: Set<PlaybackFailurePoint> = emptySet()) : PlaybackFailureInjector {
    private val failures = initial.toMutableSet()

    fun enable(point: PlaybackFailurePoint) { failures += point }
    fun disable(point: PlaybackFailurePoint) { failures -= point }

    override fun check(point: PlaybackFailurePoint): Result<Unit> =
        if (point in failures) Result.failure(PlaybackFailureInjectionException(point)) else Result.success(Unit)
}
