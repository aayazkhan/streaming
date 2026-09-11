package com.streaming.platform.playbackservice

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.datetime.Instant
import io.ktor.http.HttpStatusCode
import java.time.Duration
import java.time.ZoneOffset
import java.util.Date

data class PlaybackConfig(
    val issuer: String,
    val audience: String,
    val signingSecret: String,
    val cdnBaseUrl: String,
    val sessionTtl: Duration = Duration.ofMinutes(15),
)

class PlaybackTokenSigner(private val config: PlaybackConfig) {
    private val algorithm = Algorithm.HMAC256(config.signingSecret)

    fun issue(userId: String, profileId: String, contentId: String, sessionId: String, expiresAt: Instant, resourceKeys: List<String> = emptyList(), resourcePrefixes: List<String> = emptyList()): String = JWT.create()
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .withSubject(userId)
        .withClaim("profile_id", profileId)
        .withClaim("content_id", contentId)
        .withClaim("session_id", sessionId)
        .withArrayClaim("resource_keys", resourceKeys.map { it.trimStart('/') }.distinct().toTypedArray())
        .withArrayClaim("resource_prefixes", resourcePrefixes.map { it.trimStart('/').trimEnd('/') + "/" }.distinct().toTypedArray())
        .withIssuedAt(Date())
        .withExpiresAt(Date.from(expiresAt.toJavaInstant()))
        .sign(algorithm)

    private fun Instant.toJavaInstant(): java.time.Instant = java.time.Instant.ofEpochMilli(toEpochMilliseconds())
}

data class PlaybackGrantClaims(
    val userId: String,
    val profileId: String,
    val contentId: String,
    val sessionId: String,
    val expiresAt: Instant,
    val resourceKeys: Set<String>,
    val resourcePrefixes: Set<String>,
)

class PlaybackTokenValidator(private val config: PlaybackConfig) {
    private val algorithm = Algorithm.HMAC256(config.signingSecret)

    fun validate(token: String): PlaybackGrantClaims = try {
        val payload = JWT.require(algorithm)
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .build()
            .verify(token)
        PlaybackGrantClaims(
            userId = payload.subject ?: error("MISSING_SUBJECT"),
            profileId = payload.getClaim("profile_id").asString() ?: error("MISSING_PROFILE"),
            contentId = payload.getClaim("content_id").asString() ?: error("MISSING_CONTENT"),
            sessionId = payload.getClaim("session_id").asString() ?: error("MISSING_SESSION"),
            expiresAt = Instant.fromEpochMilliseconds(payload.expiresAt.time),
            resourceKeys = payload.getClaim("resource_keys").asList(String::class.java).orEmpty().map { it.trimStart('/') }.toSet(),
            resourcePrefixes = payload.getClaim("resource_prefixes").asList(String::class.java).orEmpty().map { it.trimStart('/').trimEnd('/') + "/" }.toSet(),
        )
    } catch (error: Throwable) {
        throw PlaybackException(HttpStatusCode.Unauthorized, "INVALID_PLAYBACK_GRANT", "Playback grant is invalid or expired")
    }

    fun allowsResource(claims: PlaybackGrantClaims, requestedResource: String): Boolean =
        requestedResource.trimStart('/').let { resource ->
            claims.resourceKeys.contains(resource) || claims.resourcePrefixes.any(resource::startsWith)
        }
}
