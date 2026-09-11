package com.streaming.platform.drm

import com.streaming.platform.playback.DrmLicenseRequest
import com.streaming.platform.playback.DrmSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.datetime.Instant
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class DrmProvidersTest {
    @Test
    fun providerSendsSystemSessionAndAuthorizationAndMapsLicenseExpiry() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("Bearer provider-token", request.headers[HttpHeaders.Authorization])
                    assertEquals("WIDEVINE", request.headers["X-Drm-System"])
                    assertEquals("session-1", request.headers["X-Drm-Session"])
                    respond(
                        content = byteArrayOf(1, 2, 3),
                        status = HttpStatusCode.OK,
                        headers = headersOf("X-License-Expires-At", "2030-01-01T00:00:00Z"),
                    )
                }
            }
        }
        val provider = WidevineProvider(
            DrmProviderConfig(DrmSystem.WIDEVINE, "https://drm.example/license", "provider-token"),
            client,
        )

        val response = provider.requestLicense(DrmLicenseRequest(DrmSystem.WIDEVINE, "content-1", "session-1", byteArrayOf(9), "https://drm.example/license")).getOrThrow()

        assertContentEquals(byteArrayOf(1, 2, 3), response.license)
        assertEquals(Instant.parse("2030-01-01T00:00:00Z"), response.expiresAt)
        client.close()
    }
}
