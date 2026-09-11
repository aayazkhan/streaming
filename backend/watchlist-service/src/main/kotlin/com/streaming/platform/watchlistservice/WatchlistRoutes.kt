package com.streaming.platform.watchlistservice

import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

private fun JWTPrincipal.userId(): String = payload.subject ?: error("JWT subject is missing")

fun Route.watchlistRoutes(repository: JdbcWatchlistRepository) {
    route("/watchlist") {
        get {
            val userId = call.principal<JWTPrincipal>()?.userId() ?: error("Unauthenticated request")
            call.respond(repository.list(userId, call.request.queryParameters["limit"]?.toIntOrNull() ?: 20, call.request.queryParameters["cursor"]).getOrThrow())
        }
        put("/{contentId}") {
            val userId = call.principal<JWTPrincipal>()?.userId() ?: error("Unauthenticated request")
            call.respond(repository.add(userId, call.parameters["contentId"] ?: "").getOrThrow())
        }
        delete("/{contentId}") {
            val userId = call.principal<JWTPrincipal>()?.userId() ?: error("Unauthenticated request")
            repository.remove(userId, call.parameters["contentId"] ?: "").getOrThrow()
            call.respond(io.ktor.http.HttpStatusCode.NoContent)
        }
    }
}
