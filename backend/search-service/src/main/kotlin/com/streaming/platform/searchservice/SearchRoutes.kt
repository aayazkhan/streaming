package com.streaming.platform.searchservice

import com.streaming.platform.search.SearchQuery
import com.streaming.platform.search.SearchRepository
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

private fun JWTPrincipal.userId(): String? = payload.subject

fun Route.searchRoutes(repository: SearchRepository) {
    route("/search") {
        get {
            val principal = call.principal<JWTPrincipal>()
            call.respond(repository.search(
                SearchQuery(
                    query = call.request.queryParameters["q"] ?: "",
                    type = call.request.queryParameters["type"]?.uppercase()?.let { runCatching { com.streaming.platform.content.ContentType.valueOf(it) }.getOrNull() },
                    genre = call.request.queryParameters["genre"],
                    releaseYear = call.request.queryParameters["releaseYear"]?.toIntOrNull(),
                    cursor = call.request.queryParameters["cursor"],
                    limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20,
                ),
                principal?.userId(),
            ).getOrThrow())
        }
        get("/autocomplete") {
            call.respond(repository.autocomplete(call.request.queryParameters["q"] ?: "", call.request.queryParameters["limit"]?.toIntOrNull() ?: 10).getOrThrow())
        }
    }
}

fun Route.searchHistoryRoutes(repository: SearchRepository) {
    get("/search/history") {
        val userId = call.principal<JWTPrincipal>()?.userId() ?: error("Unauthenticated request")
        call.respond(repository.history(userId, call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).getOrThrow())
    }
}
