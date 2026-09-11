package com.streaming.platform.identity

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.streaming.platform.security.TokenPair
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date

data class TokenIssuerConfig(
    val issuer: String,
    val audience: String,
    val secret: String,
    val accessTokenTtl: Duration = Duration.ofMinutes(10),
    val refreshTokenTtl: Duration = Duration.ofDays(30),
)

class TokenIssuer(private val config: TokenIssuerConfig) {
    private val algorithm = Algorithm.HMAC256(config.secret)
    private val random = SecureRandom()

    fun issue(userId: String, now: Instant = Instant.now()): IssuedTokens = issue(userId, UserRole.USER, now)

    fun issue(userId: String, role: UserRole, now: Instant = Instant.now()): IssuedTokens {
        val accessExpiresAt = now.plus(config.accessTokenTtl)
        val accessToken = JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withSubject(userId)
            .withClaim("role", role.name)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(accessExpiresAt))
            .sign(algorithm)
        val refreshToken = ByteArray(48).also(random::nextBytes).let {
            Base64.getUrlEncoder().withoutPadding().encodeToString(it)
        }
        return IssuedTokens(
            tokenPair = TokenPair(accessToken, refreshToken, kotlinx.datetime.Instant.fromEpochMilliseconds(accessExpiresAt.toEpochMilli())),
            refreshTokenExpiresAt = kotlinx.datetime.Instant.fromEpochMilliseconds(now.plus(config.refreshTokenTtl).toEpochMilli()),
        )
    }

    fun verifyAccessToken(token: String) = JWT.require(algorithm)
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .build()
        .verify(token)
}

data class IssuedTokens(
    val tokenPair: TokenPair,
    val refreshTokenExpiresAt: kotlinx.datetime.Instant,
)
