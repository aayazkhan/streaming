package com.streaming.platform.drm

import com.streaming.platform.playback.DrmLicenseRequest
import com.streaming.platform.playback.DrmLicenseResponse
import com.streaming.platform.playback.DrmReleaseRequest
import com.streaming.platform.playback.DrmRenewalRequest
import com.streaming.platform.playback.DrmSystem
import com.streaming.platform.playback.DrmService
import com.streaming.platform.playback.OfflineLicense
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.time.Duration

data class DrmProviderConfig(
    val system: DrmSystem,
    val licenseEndpoint: String,
    val authorizationToken: String,
    val timeout: Duration = Duration.ofSeconds(15),
)

class DrmProviderException(val status: HttpStatusCode, override val message: String) : RuntimeException(message)

/** Server-side license transport. Widevine/FairPlay/PlayReady SDK objects remain native client concerns. */
class HttpDrmProvider(
    private val config: DrmProviderConfig,
    private val client: HttpClient,
) : DrmService {
    override suspend fun requestLicense(request: DrmLicenseRequest): Result<DrmLicenseResponse> = runCatching {
        require(request.system == config.system) { "DRM_SYSTEM_MISMATCH" }
        val response = postChallenge(request.sessionId, request.challenge, request.headers)
        DrmLicenseResponse(config.system, response.body<ByteArray>(), response.headers["X-License-Expires-At"]?.let(Instant::parse))
    }

    override suspend fun renewLicense(request: DrmRenewalRequest): Result<DrmLicenseResponse> = runCatching {
        val response = postChallenge(request.sessionId, request.license, mapOf("X-Drm-Operation" to "renew"))
        DrmLicenseResponse(config.system, response.body<ByteArray>(), response.headers["X-License-Expires-At"]?.let(Instant::parse))
    }

    override suspend fun releaseLicense(request: DrmReleaseRequest): Result<Unit> = runCatching {
        postChallenge(request.sessionId, request.license, mapOf("X-Drm-Operation" to "release"))
        Unit
    }

    override suspend fun acquireOfflineLicense(request: DrmLicenseRequest): Result<OfflineLicense> = runCatching {
        val response = requestLicense(request).getOrThrow()
        OfflineLicense(request.contentId, config.system, response.license, Clock.System.now(), response.expiresAt)
    }

    private suspend fun postChallenge(sessionId: String, challenge: ByteArray, requestHeaders: Map<String, String>): HttpResponse {
        val response = client.post(config.licenseEndpoint) {
            contentType(ContentType.Application.OctetStream)
            header("Authorization", "Bearer ${config.authorizationToken}")
            header("X-Drm-System", config.system.name)
            header("X-Drm-Session", sessionId)
            requestHeaders.forEach { (key, value) -> header(key, value) }
            setBody(challenge)
        }
        if (response.status != HttpStatusCode.OK) throw DrmProviderException(response.status, "DRM_LICENSE_REQUEST_FAILED")
        return response
    }
}

class WidevineProvider(config: DrmProviderConfig, client: HttpClient) : DrmService by HttpDrmProvider(config.copy(system = DrmSystem.WIDEVINE), client)
class FairPlayProvider(config: DrmProviderConfig, client: HttpClient) : DrmService by HttpDrmProvider(config.copy(system = DrmSystem.FAIRPLAY), client)
class PlayReadyProvider(config: DrmProviderConfig, client: HttpClient) : DrmService by HttpDrmProvider(config.copy(system = DrmSystem.PLAYREADY), client)
