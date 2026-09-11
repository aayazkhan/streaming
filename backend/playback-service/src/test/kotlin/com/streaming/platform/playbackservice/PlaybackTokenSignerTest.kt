package com.streaming.platform.playbackservice

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.time.Duration

class PlaybackTokenSignerTest {
    @Test
    fun tokenContainsCdnAuthorizationClaimsAndExpiry() {
        val config = PlaybackConfig("issuer", "audience", "a-signing-secret-that-is-long-enough", "https://cdn.example/media")
        val signer = PlaybackTokenSigner(config)
        val expiresAt = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds() + Duration.ofMinutes(5).toMillis())

        val token = signer.issue("user-1", "profile-1", "content-1", "session-1", expiresAt, listOf("hls/master.m3u8"), listOf("hls"))
        val decoded = JWT.require(Algorithm.HMAC256(config.signingSecret)).withIssuer(config.issuer).withAudience(config.audience).build().verify(token)

        assertEquals("user-1", decoded.subject)
        assertEquals("profile-1", decoded.getClaim("profile_id").asString())
        assertEquals("content-1", decoded.getClaim("content_id").asString())
        assertEquals("session-1", decoded.getClaim("session_id").asString())
        assertEquals(listOf("hls/master.m3u8"), decoded.getClaim("resource_keys").asList(String::class.java))
        assertEquals(listOf("hls/"), decoded.getClaim("resource_prefixes").asList(String::class.java))
    }

    @Test
    fun validatorRejectsTamperedGrantAndReturnsScope() {
        val config = PlaybackConfig("issuer", "audience", "a-signing-secret-that-is-long-enough", "https://cdn.example/media")
        val signer = PlaybackTokenSigner(config)
        val expiresAt = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds() + Duration.ofMinutes(5).toMillis())
        val token = signer.issue("user-1", "profile-1", "content-1", "session-1", expiresAt, listOf("hls/master.m3u8"), listOf("hls"))
        val validator = PlaybackTokenValidator(config)
        val claims = validator.validate(token)

        assertEquals("content-1", claims.contentId)
        kotlin.test.assertTrue(validator.allowsResource(claims, "hls/master.m3u8"))
        kotlin.test.assertTrue(validator.allowsResource(claims, "hls/360p/segment-00001.ts"))
        kotlin.test.assertFalse(validator.allowsResource(claims, "other/master.m3u8"))
        assertFailsWith<PlaybackException> { PlaybackTokenValidator(config).validate("$token-tampered") }
    }
}
