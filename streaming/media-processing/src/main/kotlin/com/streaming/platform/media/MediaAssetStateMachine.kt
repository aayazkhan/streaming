package com.streaming.platform.media

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class MediaAssetState { CREATED, UPLOADING, UPLOADED, VALIDATING, QUEUED, PROCESSING, PACKAGING, READY, FAILED, CANCELLED }

@Serializable
enum class MediaPipelineStage { VALIDATION, TRANSCODING, PACKAGING, THUMBNAIL, SUBTITLE, ENCRYPTION, DRM, CDN_PUBLISH }

@Serializable
data class MediaAssetLifecycle(
    val assetId: String,
    val contentId: String,
    val state: MediaAssetState,
    val stage: MediaPipelineStage? = null,
    val stageProgressPercent: Int = 0,
    val failureCode: String? = null,
    val updatedAt: Instant,
)

class MediaAssetStateMachine(private val now: () -> Instant = { Clock.System.now() }) {
    fun transition(asset: MediaAssetLifecycle, next: MediaAssetState, stage: MediaPipelineStage? = asset.stage, progressPercent: Int = 0, failureCode: String? = null): MediaAssetLifecycle {
        require(progressPercent in 0..100) { "Asset progress must be between 0 and 100" }
        require(isAllowed(asset.state, next)) { "Invalid media asset transition: ${asset.state} -> $next" }
        return asset.copy(state = next, stage = stage, stageProgressPercent = progressPercent, failureCode = failureCode, updatedAt = now())
    }

    private fun isAllowed(current: MediaAssetState, next: MediaAssetState): Boolean = when (current) {
        MediaAssetState.CREATED -> next == MediaAssetState.UPLOADING || next == MediaAssetState.CANCELLED
        MediaAssetState.UPLOADING -> next == MediaAssetState.UPLOADED || next == MediaAssetState.FAILED || next == MediaAssetState.CANCELLED
        MediaAssetState.UPLOADED -> next == MediaAssetState.VALIDATING || next == MediaAssetState.FAILED || next == MediaAssetState.CANCELLED
        MediaAssetState.VALIDATING -> next == MediaAssetState.QUEUED || next == MediaAssetState.FAILED || next == MediaAssetState.CANCELLED
        MediaAssetState.QUEUED -> next == MediaAssetState.PROCESSING || next == MediaAssetState.FAILED || next == MediaAssetState.CANCELLED
        MediaAssetState.PROCESSING -> next == MediaAssetState.PACKAGING || next == MediaAssetState.FAILED || next == MediaAssetState.CANCELLED
        MediaAssetState.PACKAGING -> next == MediaAssetState.READY || next == MediaAssetState.FAILED || next == MediaAssetState.CANCELLED
        MediaAssetState.READY, MediaAssetState.FAILED, MediaAssetState.CANCELLED -> false
    }
}
