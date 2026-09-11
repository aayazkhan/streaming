package com.streaming.platform.playbackservice

import com.streaming.platform.playback.PlaybackTelemetryEvent
import com.streaming.platform.playback.StartPlaybackCommand
import com.streaming.platform.playback.UpdatePositionCommand
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private fun JWTPrincipal.userId(): String = payload.subject ?: error("JWT subject is missing")

fun Route.playbackRoutes(repository: JdbcPlaybackRepository) {
    route("/playback") {
        post("/sessions") {
            val userId = call.principal<JWTPrincipal>()?.userId() ?: error("Unauthenticated request")
            call.respond(repository.start(call.receive<StartPlaybackCommand>(), userId).getOrThrow())
        }
        patch("/sessions/{sessionId}/position") {
            val userId = call.principal<JWTPrincipal>()?.userId() ?: error("Unauthenticated request")
            val sessionId = call.parameters["sessionId"] ?: throw PlaybackException(io.ktor.http.HttpStatusCode.BadRequest, "MISSING_SESSION_ID", "Session id is required")
            call.respond(repository.updatePosition(sessionId, userId, call.receive<UpdatePositionCommand>()).getOrThrow())
        }
        post("/sessions/{sessionId}/events") {
            val userId = call.principal<JWTPrincipal>()?.userId() ?: error("Unauthenticated request")
            val sessionId = call.parameters["sessionId"] ?: throw PlaybackException(io.ktor.http.HttpStatusCode.BadRequest, "MISSING_SESSION_ID", "Session id is required")
            val event = call.receive<PlaybackTelemetryEvent>()
            if (event.sessionId != sessionId) {
                throw PlaybackException(io.ktor.http.HttpStatusCode.BadRequest, "SESSION_ID_MISMATCH", "Event session does not match path")
            }
            call.respond(repository.recordEvent(userId, event).getOrThrow())
        }
    }
    get("/continue-watching") {
        val userId = call.principal<JWTPrincipal>()?.userId() ?: error("Unauthenticated request")
        call.respond(repository.continueWatching(userId, call.request.queryParameters["profileId"], call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).getOrThrow())
    }
}

fun Route.playbackGrantValidationRoutes(
    repository: JdbcPlaybackRepository,
    validator: PlaybackTokenValidator,
) {
    get("/playback/grants/validate") {
        val token = call.request.queryParameters["token"]
            ?: call.request.headers["X-Playback-Grant"]
            ?: throw PlaybackException(io.ktor.http.HttpStatusCode.BadRequest, "MISSING_PLAYBACK_GRANT", "Playback grant is required")
        val claims = validator.validate(token)
        val requestedResource = call.request.queryParameters["resource"]
            ?: call.request.headers["X-Playback-Resource"]
            ?: throw PlaybackException(io.ktor.http.HttpStatusCode.BadRequest, "MISSING_PLAYBACK_RESOURCE", "Requested media resource is required")
        if (!validator.allowsResource(claims, requestedResource)) {
            throw PlaybackException(io.ktor.http.HttpStatusCode.Forbidden, "PLAYBACK_RESOURCE_MISMATCH", "Playback grant is not valid for this resource")
        }
        if (!repository.isGrantActive(claims)) {
            throw PlaybackException(io.ktor.http.HttpStatusCode.Unauthorized, "PLAYBACK_SESSION_INACTIVE", "Playback session is inactive or expired")
        }
        call.respond(mapOf("valid" to true, "sessionId" to claims.sessionId, "contentId" to claims.contentId, "expiresAt" to claims.expiresAt))
    }
}
