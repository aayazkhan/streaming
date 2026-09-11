package com.streaming.platform.playback

import com.streaming.platform.common.ContentId
import com.streaming.platform.common.ProfileId
import com.streaming.platform.content.ContentSummary
import kotlinx.datetime.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
enum class PlaybackProtocol { HLS, DASH }

@Serializable
enum class DrmSystem { WIDEVINE, FAIRPLAY, PLAYREADY }

@Serializable
enum class DrmSessionState { NONE, REQUESTING, ACTIVE, RENEWING, RELEASED, EXPIRED, ERROR }

@Serializable
data class DrmLicenseRequest(
    val system: DrmSystem,
    val contentId: ContentId,
    val sessionId: String,
    val challenge: ByteArray,
    val licenseUrl: String,
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class DrmLicenseResponse(val system: DrmSystem, val license: ByteArray, val expiresAt: Instant? = null)

@Serializable
data class DrmRenewalRequest(val sessionId: String, val license: ByteArray, val licenseUrl: String)

@Serializable
data class DrmReleaseRequest(val sessionId: String, val license: ByteArray, val licenseUrl: String)

@Serializable
data class OfflineLicense(
    val contentId: ContentId,
    val system: DrmSystem,
    val keySetId: ByteArray,
    val acquiredAt: Instant,
    val expiresAt: Instant?,
)

interface DrmService {
    suspend fun requestLicense(request: DrmLicenseRequest): Result<DrmLicenseResponse>
    suspend fun renewLicense(request: DrmRenewalRequest): Result<DrmLicenseResponse>
    suspend fun releaseLicense(request: DrmReleaseRequest): Result<Unit>
    suspend fun acquireOfflineLicense(request: DrmLicenseRequest): Result<OfflineLicense>
}

@Serializable
enum class DownloadState { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, EXPIRED, DELETED }

@Serializable
data class OfflineDownload(
    val id: String,
    val contentId: ContentId,
    val profileId: ProfileId,
    val state: DownloadState,
    val requestedQuality: String,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long? = null,
    val expiresAt: Instant? = null,
    val updatedAt: Instant,
)

@Serializable
data class DownloadPolicy(
    val wifiOnly: Boolean = false,
    val maxStorageBytes: Long = 10L * 1024 * 1024 * 1024,
    val maxConcurrentDownloads: Int = 2,
)

interface OfflineDownloadRepository {
    suspend fun list(profileId: ProfileId): Result<List<OfflineDownload>>
    suspend fun save(download: OfflineDownload): Result<Unit>
    suspend fun delete(downloadId: String): Result<Unit>
}

interface NativeDownloadAdapter {
    suspend fun enqueue(download: OfflineDownload, grant: PlaybackGrant): Result<Unit>
    suspend fun pause(downloadId: String): Result<Unit>
    suspend fun resume(downloadId: String): Result<Unit>
    suspend fun delete(downloadId: String): Result<Unit>
}

@Serializable
enum class PlaybackEventType {
    START,
    FIRST_FRAME,
    PLAY,
    PAUSE,
    SEEK,
    BUFFER_START,
    BUFFER_END,
    BUFFERING,
    QUALITY_CHANGED,
    AUDIO_CHANGED,
    SUBTITLE_CHANGED,
    ERROR,
    COMPLETE,
    HEARTBEAT,
}

@Serializable
enum class PlaybackState { IDLE, PREPARING, READY, PLAYING, PAUSED, BUFFERING, ENDED, ERROR }

@Serializable
data class PlayerEvent(
    val state: PlaybackState,
    val positionSeconds: Long,
    val bufferedPositionSeconds: Long? = null,
    val selectedAudioLanguage: String? = null,
    val selectedSubtitleLanguage: String? = null,
    val quality: String? = null,
    val errorCode: String? = null,
)

fun interface PlaybackEventSink {
    fun onPlayerEvent(event: PlayerEvent)
}

fun interface PlaybackTelemetrySink {
    fun onTelemetry(event: PlaybackTelemetryEvent)
}

class PlaybackStateStore(
    initial: PlayerEvent = PlayerEvent(PlaybackState.IDLE, positionSeconds = 0),
) : PlaybackEventSink {
    private val mutableState = MutableStateFlow(initial)
    val state: StateFlow<PlayerEvent> = mutableState.asStateFlow()

    override fun onPlayerEvent(event: PlayerEvent) {
        mutableState.value = event
    }
}

@Serializable
data class PlaybackSelection(
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null,
)

@Serializable
data class PlaybackSession(
    val id: String,
    val userId: String,
    val profileId: ProfileId,
    val contentId: ContentId,
    val startedAt: Instant,
    val expiresAt: Instant,
)

@Serializable
data class PlaybackGrant(
    val session: PlaybackSession,
    val hlsUrl: String?,
    val dashUrl: String?,
    val playbackToken: String,
    val expiresAt: Instant,
)

@Serializable
data class PlaybackPosition(
    val sessionId: String,
    val contentId: ContentId,
    val positionSeconds: Long,
    val durationSeconds: Long?,
    val completed: Boolean = false,
    val updatedAt: Instant,
)

@Serializable
data class ContinueWatchingItem(
    val content: ContentSummary,
    val position: PlaybackPosition,
)

@Serializable
data class PlaybackTelemetryEvent(
    val sessionId: String,
    val type: PlaybackEventType,
    val positionSeconds: Long? = null,
    val quality: String? = null,
    val errorCode: String? = null,
    val occurredAt: Instant,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class StartPlaybackCommand(
    val contentId: ContentId,
    val profileId: ProfileId,
)

@Serializable
data class UpdatePositionCommand(
    val positionSeconds: Long,
    val durationSeconds: Long? = null,
    val completed: Boolean = false,
)

interface PlaybackRepository {
    suspend fun start(command: StartPlaybackCommand, userId: String): Result<PlaybackGrant>
    suspend fun updatePosition(sessionId: String, userId: String, command: UpdatePositionCommand): Result<PlaybackPosition>
    suspend fun recordEvent(userId: String, event: PlaybackTelemetryEvent): Result<Unit>
    suspend fun continueWatching(userId: String, profileId: ProfileId?, limit: Int): Result<List<ContinueWatchingItem>>
}

interface NativePlayerAdapter {
    fun attachEventSink(sink: PlaybackEventSink) {}
    fun attachTelemetrySink(sink: PlaybackTelemetrySink) {}
    suspend fun prepare(grant: PlaybackGrant, preferredProtocol: PlaybackProtocol? = null)
    suspend fun play()
    suspend fun pause()
    suspend fun seekTo(positionSeconds: Long)
    suspend fun release()
}
