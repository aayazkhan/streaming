package com.streaming.platform.contentservice

import com.streaming.platform.content.ContentQuery
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.contentRoutes(repository: JdbcContentRepository) {
    get("/home") { call.respond(repository.home().getOrThrow()) }
    route("/content") {
        get {
            call.respond(
                repository.list(
                    ContentQuery(
                        type = call.request.queryParameters["type"]?.uppercase()?.let { runCatching { com.streaming.platform.content.ContentType.valueOf(it) }.getOrNull() },
                        genre = call.request.queryParameters["genre"],
                        releaseYear = call.request.queryParameters["releaseYear"]?.toIntOrNull(),
                        cursor = call.request.queryParameters["cursor"],
                        limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20,
                    ),
                ).getOrThrow(),
            )
        }
        get("/{contentId}") {
            call.respond(repository.findById(call.parameters["contentId"] ?: "").getOrThrow())
        }
        get("/{contentId}/similar") {
            call.respond(repository.similar(call.parameters["contentId"] ?: "", call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).getOrThrow())
        }
    }
}
