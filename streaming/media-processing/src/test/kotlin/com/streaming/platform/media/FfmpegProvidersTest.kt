package com.streaming.platform.media

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import java.nio.file.Files
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertTrue

class FfmpegProvidersTest {
    @Test
    fun transcoderUsesArgumentListAndNeverShellInterpolatesInput() = runBlocking {
        val workspace = Files.createTempDirectory("ffmpeg-test")
        val source = workspace.resolve("input.mp4")
        Files.write(source, byteArrayOf(1))
        val commands = mutableListOf<List<String>>()
        FfmpegVideoTranscoder(FfmpegConfig(workspace = workspace), ProcessCommandRunner { command, _ -> commands += command })
            .transcode("input.mp4", "media/content/job", listOf(TranscodeProfile("720p", 1280, 720, 3000)))
        assertTrue(commands.single().any { it.endsWith("/input.mp4") })
        assertTrue(commands.single().any { it.endsWith("/media/content/job/720p.mp4") })
    }

    @Test
    fun pipelineCarriesProviderArtifactsIntoReadyJob() = runBlocking {
        val now = Clock.System.now()
        val result = MediaPipelineOrchestrator(
            storage = object : MediaObjectStorage {
                override suspend fun exists(key: String) = true
                override suspend fun put(key: String, bytes: ByteArray, contentType: String) = Unit
                override suspend fun delete(key: String) = Unit
            },
            transcoder = object : VideoTranscoder { override suspend fun transcode(sourceKey: String, outputPrefix: String, profiles: List<TranscodeProfile>) = outputPrefix },
            packager = object : AdaptiveBitratePackager { override suspend fun packageHlsAndDash(transcodeOutputPrefix: String, outputPrefix: String) = PackagedMedia("hls.m3u8", "dash.mpd") },
            thumbnails = object : ThumbnailGenerator { override suspend fun generate(sourceKey: String, outputPrefix: String) = GeneratedThumbnails("poster.jpg", "preview.jpg") },
            jobs = object : MediaJobStore { override suspend fun save(job: MediaJob) = Unit; override suspend fun update(job: MediaJob) = Unit },
        ).process(MediaJob("job", "content", "source", MediaJobState.ACCEPTED, listOf(TranscodeProfile("720p", 1280, 720, 3000)), now, now))
        assertTrue(result.hlsManifestKey == "hls.m3u8" && result.posterKey == "poster.jpg")
    }
}
