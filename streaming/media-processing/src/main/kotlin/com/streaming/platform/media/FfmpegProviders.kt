package com.streaming.platform.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration

data class FfmpegConfig(
    val binary: String = "ffmpeg",
    val workspace: Path,
    val timeout: Duration = Duration.ofHours(2),
)

fun interface ProcessCommandRunner {
    suspend fun run(command: List<String>, timeout: Duration)
}

class JvmProcessCommandRunner : ProcessCommandRunner {
    override suspend fun run(command: List<String>, timeout: Duration) = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            error("FFMPEG_TIMEOUT")
        }
        check(process.exitValue() == 0) { "FFMPEG_FAILED: ${output.takeLast(800)}" }
    }
}

/** Builds and executes one FFmpeg encode per rendition in a worker workspace. */
class FfmpegVideoTranscoder(
    private val config: FfmpegConfig,
    private val runner: ProcessCommandRunner = JvmProcessCommandRunner(),
) : VideoTranscoder {
    override suspend fun transcode(sourceKey: String, outputPrefix: String, profiles: List<TranscodeProfile>): String {
        require(profiles.isNotEmpty()) { "At least one transcode profile is required" }
        val source = config.workspace.resolve(sourceKey).normalize()
        val outputDirectory = config.workspace.resolve(outputPrefix).normalize()
        require(source.startsWith(config.workspace.normalize())) { "Source path escapes worker workspace" }
        require(outputDirectory.startsWith(config.workspace.normalize())) { "Output path escapes worker workspace" }
        check(Files.exists(source)) { "MEDIA_SOURCE_NOT_FOUND" }
        Files.createDirectories(outputDirectory)
        profiles.forEach { profile ->
            val output = outputDirectory.resolve("${profile.name}.mp4")
            runner.run(
                listOf(
                    config.binary, "-y", "-i", source.toString(),
                    // force_divisible_by=2 keeps the aspect-preserved output dimensions even — libx264's
                    // yuv420p chroma subsampling rejects odd width/height, which any source whose aspect
                    // ratio doesn't exactly match the profile's w:h would otherwise hit after "decrease" scaling.
                    "-vf", "scale=w=${profile.width}:h=${profile.height}:force_original_aspect_ratio=decrease:force_divisible_by=2",
                    "-c:v", if (profile.codec == "h264") "libx264" else profile.codec,
                    "-b:v", "${profile.bitrateKbps}k", "-c:a", "aac", "-movflags", "+faststart", output.toString(),
                ),
                config.timeout,
            )
        }
        return outputPrefix
    }
}

class FfmpegAdaptiveBitratePackager(
    private val config: FfmpegConfig,
    private val runner: ProcessCommandRunner = JvmProcessCommandRunner(),
) : AdaptiveBitratePackager {
    override suspend fun packageHlsAndDash(transcodeOutputPrefix: String, outputPrefix: String): PackagedMedia {
        val inputDirectory = config.workspace.resolve(transcodeOutputPrefix).normalize()
        val outputDirectory = config.workspace.resolve(outputPrefix).normalize()
        Files.createDirectories(outputDirectory.resolve("hls"))
        Files.createDirectories(outputDirectory.resolve("dash"))
        val firstRendition = Files.list(inputDirectory).use { stream -> stream.filter { it.toString().endsWith(".mp4") }.findFirst().orElseThrow { IllegalStateException("NO_TRANSCODED_RENDITIONS") } }
        runner.run(
            listOf(config.binary, "-y", "-i", firstRendition.toString(), "-f", "hls", "-hls_time", "6", "-hls_playlist_type", "vod", outputDirectory.resolve("hls/index.m3u8").toString()),
            config.timeout,
        )
        runner.run(
            listOf(config.binary, "-y", "-i", firstRendition.toString(), "-f", "dash", "-seg_duration", "6", outputDirectory.resolve("dash/manifest.mpd").toString()),
            config.timeout,
        )
        return PackagedMedia("$outputPrefix/hls/index.m3u8", "$outputPrefix/dash/manifest.mpd")
    }
}

class FfmpegThumbnailGenerator(
    private val config: FfmpegConfig,
    private val runner: ProcessCommandRunner = JvmProcessCommandRunner(),
) : ThumbnailGenerator {
    override suspend fun generate(sourceKey: String, outputPrefix: String): GeneratedThumbnails {
        val source = config.workspace.resolve(sourceKey).normalize()
        val outputDirectory = config.workspace.resolve(outputPrefix).normalize()
        Files.createDirectories(outputDirectory)
        runner.run(listOf(config.binary, "-y", "-ss", "00:00:03", "-i", source.toString(), "-frames:v", "1", outputDirectory.resolve("poster.jpg").toString()), config.timeout)
        runner.run(listOf(config.binary, "-y", "-i", source.toString(), "-vf", "fps=1/10,scale=320:-1", outputDirectory.resolve("preview-%03d.jpg").toString()), config.timeout)
        return GeneratedThumbnails("$outputPrefix/poster.jpg", "$outputPrefix/preview-%03d.jpg")
    }
}

/** Copies validated WebVTT sidecars into the package workspace; caption authoring remains an ingestion concern. */
class SidecarSubtitleProcessor(
    private val config: FfmpegConfig,
    private val sidecarsByLanguage: Map<String, String>,
) : SubtitleProcessor {
    override suspend fun process(sourceKey: String, outputPrefix: String): GeneratedSubtitles = withContext(Dispatchers.IO) {
        val outputDirectory = config.workspace.resolve(outputPrefix).normalize().resolve("subtitles")
        Files.createDirectories(outputDirectory)
        GeneratedSubtitles(sidecarsByLanguage.mapValues { (language, sidecarKey) ->
            require(language.matches(Regex("[a-zA-Z]{2,8}(-[a-zA-Z]{2,8})?"))) { "INVALID_SUBTITLE_LANGUAGE" }
            val source = config.workspace.resolve(sidecarKey).normalize()
            require(source.startsWith(config.workspace.normalize())) { "Subtitle path escapes worker workspace" }
            check(Files.exists(source)) { "SUBTITLE_SOURCE_NOT_FOUND" }
            check(source.toString().endsWith(".vtt", ignoreCase = true)) { "SUBTITLE_FORMAT_UNSUPPORTED" }
            val destination = outputDirectory.resolve("$language.vtt")
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
            "$outputPrefix/subtitles/$language.vtt"
        })
    }
}
