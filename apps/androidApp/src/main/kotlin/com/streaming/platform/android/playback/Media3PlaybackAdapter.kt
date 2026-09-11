package com.streaming.platform.android.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.streaming.platform.playback.NoopPlaybackFailureInjector
import com.streaming.platform.playback.NativePlayerAdapter
import com.streaming.platform.playback.PlaybackEventSink
import com.streaming.platform.playback.PlaybackEventType
import com.streaming.platform.playback.PlaybackFailureInjector
import com.streaming.platform.playback.PlaybackFailurePoint
import com.streaming.platform.playback.PlaybackGrant
import com.streaming.platform.playback.PlaybackProtocol
import com.streaming.platform.playback.PlaybackState
import com.streaming.platform.playback.PlaybackTelemetryEvent
import com.streaming.platform.playback.PlaybackTelemetrySink
import com.streaming.platform.playback.PlayerEvent
import kotlinx.datetime.Clock

/** Native Android boundary; the app module owns the Media3 dependency and lifecycle. */
class Media3PlaybackAdapter(
    private val player: ExoPlayer,
    private val failureInjector: PlaybackFailureInjector = NoopPlaybackFailureInjector,
) : NativePlayerAdapter {
    private var eventSink: PlaybackEventSink? = null
    private var telemetrySink: PlaybackTelemetrySink? = null
    private var sessionId: String? = null
    private var firstFrameReported = false
    private var wasBuffering = false

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val state = when (playbackState) {
                    Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                    Player.STATE_READY -> if (player.isPlaying) PlaybackState.PLAYING else PlaybackState.PAUSED
                    Player.STATE_ENDED -> PlaybackState.ENDED
                    else -> PlaybackState.PREPARING
                }
                if (state == PlaybackState.BUFFERING && !wasBuffering) record(PlaybackEventType.BUFFER_START)
                if (state != PlaybackState.BUFFERING && wasBuffering) record(PlaybackEventType.BUFFER_END)
                wasBuffering = state == PlaybackState.BUFFERING
                emit(state)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && !firstFrameReported) {
                    firstFrameReported = true
                    record(PlaybackEventType.FIRST_FRAME)
                }
                record(if (isPlaying) PlaybackEventType.PLAY else PlaybackEventType.PAUSE)
                emit(if (isPlaying) PlaybackState.PLAYING else PlaybackState.PAUSED)
            }

            override fun onPlayerError(error: PlaybackException) {
                record(PlaybackEventType.ERROR, errorCode = error.errorCodeName)
                emit(PlaybackState.ERROR, error.errorCodeName)
            }
        })
    }

    override fun attachEventSink(sink: PlaybackEventSink) { eventSink = sink }
    override fun attachTelemetrySink(sink: PlaybackTelemetrySink) { telemetrySink = sink }

    override suspend fun prepare(grant: PlaybackGrant, preferredProtocol: PlaybackProtocol?) {
        failureInjector.check(PlaybackFailurePoint.CDN_MANIFEST).getOrThrow()
        sessionId = grant.session.id
        firstFrameReported = false
        wasBuffering = false
        record(PlaybackEventType.START)
        emit(PlaybackState.PREPARING)
        val manifest = when (preferredProtocol) {
            PlaybackProtocol.DASH -> grant.dashUrl ?: grant.hlsUrl
            PlaybackProtocol.HLS -> grant.hlsUrl ?: grant.dashUrl
            null -> grant.hlsUrl ?: grant.dashUrl
        } ?: error("PLAYBACK_MANIFEST_UNAVAILABLE")
        player.setMediaItem(MediaItem.fromUri(manifest))
        player.prepare()
    }

    override suspend fun play() { player.play(); emit(PlaybackState.PLAYING) }
    override suspend fun pause() { player.pause(); emit(PlaybackState.PAUSED) }
    override suspend fun seekTo(positionSeconds: Long) { player.seekTo(positionSeconds * 1_000L); emit(playerState()) }
    override suspend fun release() { player.release() }

    private fun emit(state: PlaybackState, errorCode: String? = null) {
        eventSink?.onPlayerEvent(PlayerEvent(state, player.currentPosition / 1_000L, player.bufferedPosition / 1_000L, errorCode = errorCode))
    }

    private fun record(type: PlaybackEventType, errorCode: String? = null) {
        val id = sessionId ?: return
        telemetrySink?.onTelemetry(PlaybackTelemetryEvent(id, type, player.currentPosition / 1_000L, errorCode = errorCode, occurredAt = Clock.System.now()))
    }

    private fun playerState() = when {
        player.playbackState == Player.STATE_BUFFERING -> PlaybackState.BUFFERING
        player.isPlaying -> PlaybackState.PLAYING
        else -> PlaybackState.PAUSED
    }
}
