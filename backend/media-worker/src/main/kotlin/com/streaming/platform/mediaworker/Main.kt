package com.streaming.platform.mediaworker

import com.streaming.platform.media.AdaptiveBitratePackager
import com.streaming.platform.media.FfmpegAdaptiveBitratePackager
import com.streaming.platform.media.FfmpegConfig
import com.streaming.platform.media.FfmpegThumbnailGenerator
import com.streaming.platform.media.FfmpegVideoTranscoder
import com.streaming.platform.media.GeneratedThumbnails
import com.streaming.platform.media.MediaJob
import com.streaming.platform.media.MediaJobWorker
import com.streaming.platform.media.MediaObjectStorage
import com.streaming.platform.media.MediaPipelineOrchestrator
import com.streaming.platform.media.MediaWorkerConfig
import com.streaming.platform.media.MediaWorkerMetricsObserver
import com.streaming.platform.media.MediaWorkerObserver
import com.streaming.platform.media.PackagedMedia
import com.streaming.platform.media.ThumbnailGenerator
import com.streaming.platform.media.TranscodeProfile
import com.streaming.platform.media.VideoTranscoder
import com.streaming.platform.mediaservice.JdbcMediaJobQueue
import com.streaming.platform.storage.S3CompatibleObjectStorage
import com.streaming.platform.storage.S3CompatibleStorageConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory

private data class WorkerEnv(
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val storage: S3CompatibleStorageConfig,
    val workspace: Path,
    val workerId: String,
    val pollInterval: Duration,
    val maxConcurrentJobs: Int,
) {
    companion object
}

fun main() = runBlocking {
    val logger = LoggerFactory.getLogger("streaming.media-worker")
    val env = WorkerEnv.fromEnvironment()
    Files.createDirectories(env.workspace)
    val dataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = env.databaseUrl
        username = env.databaseUser
        password = env.databasePassword
        maximumPoolSize = 4
        minimumIdle = 1
        poolName = "streaming-media-worker-db"
    })
    val storage = S3CompatibleObjectStorage(env.storage)
    val queue = JdbcMediaJobQueue(dataSource)
    val ffmpeg = FfmpegConfig(workspace = env.workspace)
    val workspaceStorage = WorkspaceObjectStorage(env.workspace)
    val orchestrator = MediaPipelineOrchestrator(
        storage = workspaceStorage,
        transcoder = FfmpegVideoTranscoder(ffmpeg),
        packager = FfmpegAdaptiveBitratePackager(ffmpeg),
        thumbnails = FfmpegThumbnailGenerator(ffmpeg),
        jobs = queue,
    )
    val running = AtomicBoolean(true)
    val metrics = MediaWorkerMetricsObserver()
    Runtime.getRuntime().addShutdownHook(Thread { running.set(false) })
    val worker = MediaJobWorker(
        queue = queue,
        orchestrator = orchestrator,
        config = MediaWorkerConfig(workerId = env.workerId, maxConcurrentJobs = env.maxConcurrentJobs),
        beforeProcess = { job ->
            val source = env.workspace.resolve(job.sourceKey).normalize()
            require(source.startsWith(env.workspace.normalize())) { "MEDIA_SOURCE_PATH_INVALID" }
            storage.downloadTo(job.sourceKey, source)
        },
        afterProcess = { job -> publishArtifacts(job, env.workspace, storage) },
        observer = object : MediaWorkerObserver {
            override fun onClaimed(job: MediaJob) { metrics.onClaimed(job); logger.info("media_job_claimed jobId={} workerId={} attempt={}", job.id, env.workerId, job.attempts + 1) }
            override fun onHeartbeat(job: MediaJob, renewed: Boolean) { metrics.onHeartbeat(job, renewed); logger.info("media_job_heartbeat jobId={} workerId={} renewed={}", job.id, env.workerId, renewed) }
            override fun onCompleted(job: MediaJob) { metrics.onCompleted(job); logger.info("media_job_completed jobId={} contentId={}", job.id, job.contentId) }
            override fun onCancelled(job: MediaJob) { metrics.onCancelled(job); logger.info("media_job_cancelled jobId={}", job.id) }
            override fun onFailed(job: MediaJob, failureCode: String, retryAt: kotlinx.datetime.Instant?) { metrics.onFailed(job, failureCode, retryAt); logger.error("media_job_failed jobId={} failureCode={} retryAt={}", job.id, failureCode, retryAt) }
        },
    )
    try {
        while (running.get()) {
            // Defense in depth: MediaJobWorker already guards its own per-job failure handling,
            // but this loop is the last line of defense — any exception that still escapes (a
            // future bug, a transient infra error) must not permanently kill the worker process,
            // since that stops processing every subsequent job too until someone restarts it.
            val processed = try {
                worker.processAvailable()
            } catch (error: Throwable) {
                logger.error("Unhandled error in media worker poll loop; continuing", error)
                null
            }
            if (processed == null || processed.all { it == null }) delay(env.pollInterval.toMillis())
        }
    } finally {
        logger.info("media_worker_metrics workerId={} metrics={}", env.workerId, metrics.snapshot())
        storage // Keeps the provider lifecycle explicit for the worker process.
        dataSource.close()
    }
}

private suspend fun publishArtifacts(job: MediaJob, workspace: Path, storage: S3CompatibleObjectStorage): MediaJob {
    val prefix = "media/${job.contentId}/${job.id}"
    val root = workspace.resolve(prefix).normalize()
    if (!Files.exists(root)) error("MEDIA_ARTIFACTS_NOT_FOUND")
    Files.walk(root).use { files ->
        files.filter { Files.isRegularFile(it) }.forEach { file ->
            val key = workspace.relativize(file).toString().replace('\\', '/')
            runBlocking { storage.uploadFile(key, file, contentTypeFor(file)) }
        }
    }
    return job
}

private fun contentTypeFor(path: Path): String = when (path.fileName.toString().substringAfterLast('.', "").lowercase()) {
    "m3u8" -> "application/vnd.apple.mpegurl"
    "mpd" -> "application/dash+xml"
    "vtt" -> "text/vtt"
    "jpg", "jpeg" -> "image/jpeg"
    "mp4" -> "video/mp4"
    "m4s" -> "video/iso.segment"
    "ts" -> "video/mp2t"
    else -> "application/octet-stream"
}

private class WorkspaceObjectStorage(private val root: Path) : MediaObjectStorage {
    override suspend fun exists(key: String): Boolean = root.resolve(key).normalize().let { it.startsWith(root.normalize()) && Files.exists(it) }
    override suspend fun put(key: String, bytes: ByteArray, contentType: String) {
        val path = root.resolve(key).normalize(); require(path.startsWith(root.normalize())); Files.createDirectories(path.parent); Files.write(path, bytes)
    }
    override suspend fun delete(key: String) { Files.deleteIfExists(root.resolve(key).normalize()) }
}

private fun WorkerEnv.Companion.fromEnvironment(environment: Map<String, String> = System.getenv()): WorkerEnv = WorkerEnv(
    databaseUrl = environment.required("DATABASE_URL"),
    databaseUser = environment.required("DATABASE_USER"),
    databasePassword = environment.required("DATABASE_PASSWORD"),
    storage = S3CompatibleStorageConfig(
        endpoint = environment["MEDIA_STORAGE_ENDPOINT"] ?: "http://localhost:9000",
        accessKey = environment["MEDIA_STORAGE_ACCESS_KEY"] ?: "minioadmin",
        secretKey = environment["MEDIA_STORAGE_SECRET_KEY"] ?: "minioadmin",
        bucket = environment["MEDIA_STORAGE_BUCKET"] ?: "media",
        region = environment["MEDIA_STORAGE_REGION"] ?: "us-east-1",
    ),
    workspace = Path.of(environment["MEDIA_WORKSPACE"] ?: "/tmp/streaming-media-worker"),
    workerId = environment["MEDIA_WORKER_ID"] ?: "media-worker-${ProcessHandle.current().pid()}",
    pollInterval = Duration.ofMillis(environment["MEDIA_POLL_INTERVAL_MS"]?.toLongOrNull()?.coerceAtLeast(250) ?: 1_000),
    maxConcurrentJobs = (environment["MEDIA_MAX_CONCURRENT_JOBS"]?.toIntOrNull() ?: 2).coerceIn(1, 8),
)

private fun Map<String, String>.required(name: String) = get(name)?.takeIf(String::isNotBlank) ?: error("Missing required environment variable: $name")
