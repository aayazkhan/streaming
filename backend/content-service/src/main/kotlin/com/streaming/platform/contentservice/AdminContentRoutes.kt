package com.streaming.platform.contentservice

import com.streaming.platform.content.ContentQuery
import com.streaming.platform.content.ContentStatus
import com.streaming.platform.content.ContentType
import com.streaming.platform.content.CreateContentCommand
import com.streaming.platform.content.UpdateContentCommand
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

private fun JWTPrincipal.requireAdmin() {
    if (payload.getClaim("role").asString() != "ADMIN") {
        throw ContentException(HttpStatusCode.Forbidden, "CONTENT_ADMIN_REQUIRED", "Content authoring requires an administrator role")
    }
}

private fun requireAdminPrincipal(principal: JWTPrincipal?): JWTPrincipal {
    val resolved = principal ?: throw ContentException(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "Authentication is required")
    resolved.requireAdmin()
    return resolved
}

fun Route.adminContentRoutes(repository: JdbcContentRepository) {
    route("/admin/content") {
        get {
            requireAdminPrincipal(call.principal<JWTPrincipal>())
            val status = call.request.queryParameters["status"]?.uppercase()?.let {
                runCatching { ContentStatus.valueOf(it) }.getOrElse {
                    throw ContentException(HttpStatusCode.BadRequest, "INVALID_STATUS", "Status must be one of DRAFT, PUBLISHED, ARCHIVED")
                }
            }
            val query = ContentQuery(
                type = call.request.queryParameters["type"]?.uppercase()?.let { runCatching { ContentType.valueOf(it) }.getOrNull() },
                cursor = call.request.queryParameters["cursor"],
                limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20,
            )
            call.respond(repository.listForAdmin(query, status))
        }
        post {
            requireAdminPrincipal(call.principal<JWTPrincipal>())
            val command = call.receive<CreateContentCommand>()
            validateContentTitle(command.title)
            call.respond(HttpStatusCode.Created, repository.createContent(command))
        }
        get("/{contentId}") {
            requireAdminPrincipal(call.principal<JWTPrincipal>())
            val contentId = call.parameters["contentId"] ?: throw missingContentId()
            call.respond(repository.findByIdForAdmin(contentId))
        }
        put("/{contentId}") {
            requireAdminPrincipal(call.principal<JWTPrincipal>())
            val contentId = call.parameters["contentId"] ?: throw missingContentId()
            val command = call.receive<UpdateContentCommand>()
            validateContentTitle(command.title)
            call.respond(repository.updateContent(contentId, command))
        }
        delete("/{contentId}") {
            requireAdminPrincipal(call.principal<JWTPrincipal>())
            val contentId = call.parameters["contentId"] ?: throw missingContentId()
            repository.archiveContent(contentId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
    get("/admin/genres") {
        requireAdminPrincipal(call.principal<JWTPrincipal>())
        call.respond(repository.listGenres())
    }
}

private fun validateContentTitle(title: String) {
    if (title.trim().isEmpty() || title.length > 300) {
        throw ContentException(HttpStatusCode.BadRequest, "INVALID_TITLE", "Title must be 1 to 300 characters")
    }
}

private fun missingContentId() = ContentException(HttpStatusCode.BadRequest, "MISSING_CONTENT_ID", "Content id is required")
