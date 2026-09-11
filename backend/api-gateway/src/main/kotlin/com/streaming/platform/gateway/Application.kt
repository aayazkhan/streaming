package com.streaming.platform.gateway

import com.auth0.jwt.algorithms.Algorithm
import com.streaming.platform.common.ApiErrorResponse
import com.streaming.platform.identity.Argon2PasswordHasher
import com.streaming.platform.identity.IdentityException
import com.streaming.platform.identity.IdentityService
import com.streaming.platform.identity.JdbcIdentityRepository
import com.streaming.platform.identity.TokenIssuer
import com.streaming.platform.identity.identityRoutes
import com.streaming.platform.identity.adminBootstrapRoutes
import com.streaming.platform.contentservice.ContentException
import com.streaming.platform.contentservice.JdbcContentRepository
import com.streaming.platform.contentservice.contentRoutes
import com.streaming.platform.contentservice.adminContentRoutes
import com.streaming.platform.profile.JdbcProfileRepository
import com.streaming.platform.profile.ProfileException
import com.streaming.platform.profile.profileRoutes
import com.streaming.platform.playbackservice.JdbcPlaybackRepository
import com.streaming.platform.playbackservice.PlaybackConfig
import com.streaming.platform.playbackservice.PlaybackException
import com.streaming.platform.playbackservice.PlaybackTokenSigner
import com.streaming.platform.playbackservice.PlaybackTokenValidator
import com.streaming.platform.playbackservice.playbackGrantValidationRoutes
import com.streaming.platform.playbackservice.playbackRoutes
import com.streaming.platform.searchservice.JdbcSearchRepository
import com.streaming.platform.searchservice.OpenSearchConfig
import com.streaming.platform.searchservice.OpenSearchSearchIndex
import com.streaming.platform.searchservice.RoutedSearchRepository
import com.streaming.platform.searchservice.SearchException
import com.streaming.platform.searchservice.searchHistoryRoutes
import com.streaming.platform.searchservice.searchRoutes
import com.streaming.platform.watchlistservice.JdbcWatchlistRepository
import com.streaming.platform.watchlistservice.WatchlistException
import com.streaming.platform.watchlistservice.watchlistRoutes
import com.streaming.platform.mediaservice.JdbcMediaRepository
import com.streaming.platform.mediaservice.JdbcMediaJobQueue
import com.streaming.platform.mediaservice.MediaException
import com.streaming.platform.mediaservice.mediaRoutes
import com.streaming.platform.storage.S3CompatibleObjectStorage
import com.streaming.platform.storage.S3CompatibleMultipartStorage
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.application.log
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.time.Duration
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import kotlinx.serialization.json.Json
import java.util.UUID

fun Application.module(config: GatewayConfig = GatewayConfig.fromEnvironment()) {
    val dataSource = createDataSource(config)
    val applicationLog = log
    SchemaMigrator(dataSource).migrate()

    val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(CallLogging) { mdc("correlationId") { it.callId ?: "unknown" } }
    install(MicrometerMetrics) {
        registry = prometheusRegistry
        // Percentile histograms are off by default; without them there are no `_bucket` series
        // and `histogram_quantile` (used by the Grafana p50/p95/p99 dashboard) has nothing to query.
        distributionStatisticConfig = DistributionStatisticConfig.builder()
            .percentilesHistogram(true)
            .minimumExpectedValue(Duration.ofMillis(1).toNanos().toDouble())
            .maximumExpectedValue(Duration.ofSeconds(10).toNanos().toDouble())
            .build()
    }
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = false
            ignoreUnknownKeys = false
            encodeDefaults = true
            explicitNulls = false
        })
    }
    install(CORS) {
        config.corsOrigins.forEach { origin ->
            val normalized = origin.removePrefix("https://").removePrefix("http://")
            val scheme = if (origin.startsWith("https://")) "https" else "http"
            allowHost(normalized, schemes = listOf(scheme))
        }
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Correlation-Id")
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowCredentials = true
    }
    install(StatusPages) {
        exception<IdentityException> { call, cause ->
            call.respond(cause.status, cause.toError(call.callId ?: "unknown"))
        }
        exception<ProfileException> { call, cause ->
            call.respond(cause.status, ApiErrorResponse(cause.code, cause.message, call.callId ?: "unknown"))
        }
        exception<ContentException> { call, cause ->
            call.respond(cause.status, ApiErrorResponse(cause.code, cause.message, call.callId ?: "unknown"))
        }
        exception<SearchException> { call, cause ->
            call.respond(cause.status, ApiErrorResponse(cause.code, cause.message, call.callId ?: "unknown"))
        }
        exception<PlaybackException> { call, cause ->
            call.respond(cause.status, ApiErrorResponse(cause.code, cause.message, call.callId ?: "unknown"))
        }
        exception<WatchlistException> { call, cause ->
            call.respond(cause.status, ApiErrorResponse(cause.code, cause.message, call.callId ?: "unknown"))
        }
        exception<MediaException> { call, cause ->
            call.respond(cause.status, ApiErrorResponse(cause.code, cause.message, call.callId ?: "unknown"))
        }
        // Malformed/invalid JSON bodies (missing fields, wrong types, invalid enum values) throw
        // this from ContentNegotiation's deserialization step — it's a client error, not a server
        // bug, and previously fell through to the generic 500 handler below.
        exception<io.ktor.server.plugins.BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorResponse("INVALID_REQUEST_BODY", "The request body is malformed or has an invalid value", call.callId ?: "unknown"),
            )
        }
        exception<Throwable> { call, cause ->
            applicationLog.error("Unhandled request failure", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiErrorResponse("INTERNAL_ERROR", "An unexpected error occurred", call.callId ?: "unknown"),
            )
        }
    }
    install(io.ktor.server.plugins.callid.CallId) {
        retrieveFromHeader("X-Correlation-Id")
        generate { UUID.randomUUID().toString() }
        verify { it.length in 16..100 }
    }
    install(Authentication) {
        jwt("auth-jwt") {
            verifier(
                com.auth0.jwt.JWT.require(Algorithm.HMAC256(config.jwt.secret))
                    .withIssuer(config.jwt.issuer)
                    .withAudience(config.jwt.audience)
                    .build(),
            )
            validate { credential ->
                if (credential.payload.subject.isNullOrBlank()) null else JWTPrincipal(credential.payload)
            }
        }
    }

    val identityService = IdentityService(JdbcIdentityRepository(dataSource), Argon2PasswordHasher(), TokenIssuer(config.jwt))
    val profileRepository = JdbcProfileRepository(dataSource)
    val contentRepository = JdbcContentRepository(dataSource, config.publicAssetBaseUrl)
    val databaseSearchRepository = JdbcSearchRepository(dataSource, config.publicAssetBaseUrl)
    val openSearchClient = config.openSearchEndpoint?.let {
        HttpClient(CIO) {
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }
    val searchRepository = RoutedSearchRepository(
        databaseSearchRepository,
        config.openSearchEndpoint?.let { OpenSearchSearchIndex(openSearchClient!!, OpenSearchConfig(it, config.openSearchIndex, config.openSearchApiKey)) },
    )
    val playbackConfig = PlaybackConfig(config.jwt.issuer, config.jwt.audience, config.jwt.secret, config.cdnBaseUrl)
    val playbackRepository = JdbcPlaybackRepository(
        dataSource,
        playbackConfig,
        PlaybackTokenSigner(playbackConfig),
    )
    val playbackTokenValidator = PlaybackTokenValidator(playbackConfig)
    val watchlistRepository = JdbcWatchlistRepository(dataSource)
    val mediaStorage = S3CompatibleObjectStorage(config.mediaStorage)
    val multipartStorage = S3CompatibleMultipartStorage(config.mediaStorage)
    val mediaRepository = JdbcMediaRepository(dataSource)
    val mediaJobQueue = JdbcMediaJobQueue(dataSource)

    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }
        get("/ready") {
            if (dataSource.isReady()) call.respond(mapOf("status" to "ready"))
            else call.respond(HttpStatusCode.ServiceUnavailable, mapOf("status" to "not_ready"))
        }
        get("/metrics") {
            call.respondText(prometheusRegistry.scrape(), ContentType.parse("text/plain; version=0.0.4"))
        }
        route("/v1") {
            identityRoutes(identityService)
            contentRoutes(contentRepository)
            searchRoutes(searchRepository)
            playbackGrantValidationRoutes(playbackRepository, playbackTokenValidator)
            authenticate("auth-jwt") {
                profileRoutes(profileRepository)
                searchHistoryRoutes(searchRepository)
                playbackRoutes(playbackRepository)
                watchlistRoutes(watchlistRepository)
                mediaRoutes(mediaRepository, mediaStorage, multipartStorage, jobQueue = mediaJobQueue)
                adminContentRoutes(contentRepository)
            }
            // Not behind authenticate("auth-jwt") — the whole point of bootstrap is promoting an
            // account that doesn't have an admin JWT yet. The shared ADMIN_BOOTSTRAP_SECRET is the
            // only gate (see IdentityRoutes.kt).
            adminBootstrapRoutes(identityService, config.adminBootstrapSecret)
        }
    }

    monitor.subscribe(ApplicationStopped) {
        openSearchClient?.close()
        multipartStorage.close()
        dataSource.close()
    }
}

private fun IdentityException.toError(correlationId: String) = ApiErrorResponse(code, message, correlationId, details)
