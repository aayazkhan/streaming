package com.streaming.platform.media

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaPipelineOrchestratorTest {
    @Test
    fun successfulJobPersistsDurableStateTransitions() = runBlocking {
        val states = mutableListOf<MediaJobState>()
        val store = object : MediaJobStore {
            override suspend fun save(job: MediaJob) { states += job.state }
            override suspend fun update(job: MediaJob) { states += job.state }
        }
        val orchestrator = MediaPipelineOrchestrator(
            storage = object : MediaObjectStorage {
                override suspend fun exists(key: String) = true
                override suspend fun put(key: String, bytes: ByteArray, contentType: String) = Unit
                override suspend fun delete(key: String) = Unit
            },
            transcoder = object : VideoTranscoder {
                override suspend fun transcode(sourceKey: String, outputPrefix: String, profiles: List<TranscodeProfile>) = outputPrefix
            },
            packager = object : AdaptiveBitratePackager {
                override suspend fun packageHlsAndDash(transcodeOutputPrefix: String, outputPrefix: String) = PackagedMedia("hls/index.m3u8", "dash/manifest.mpd")
            },
            thumbnails = object : ThumbnailGenerator {
                override suspend fun generate(sourceKey: String, outputPrefix: String) = GeneratedThumbnails("poster.jpg", "preview.vtt")
            },
            jobs = store,
        )

        val result = orchestrator.process(
            MediaJob("job-1", "content-1", "source.mp4", MediaJobState.ACCEPTED, listOf(TranscodeProfile("720p", 1280, 720, 3_000)), Clock.System.now(), Clock.System.now()),
        )

        assertEquals(MediaJobState.READY, result.state)
        assertEquals(listOf(MediaJobState.UPLOADING, MediaJobState.TRANSCODING, MediaJobState.PACKAGING, MediaJobState.SUBTITLES, MediaJobState.THUMBNAILS, MediaJobState.READY), states)
        assertTrue(result.failureCode == null)
    }
}
