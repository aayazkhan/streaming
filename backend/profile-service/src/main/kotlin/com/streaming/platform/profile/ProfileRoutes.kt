package com.streaming.platform.profile

import com.streaming.platform.profile.CreateProfileCommand
import com.streaming.platform.profile.UpdateProfileCommand
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

private fun JWTPrincipal.userId(): String = payload.subject ?: error("JWT subject is missing")

fun Route.profileRoutes(repository: JdbcProfileRepository) {
    route("/profiles") {
        get {
            val userId = call.principal<JWTPrincipal>()?.userId() ?: error("Unauthenticated request")
            call.respond(repository.listForUser(userId))
        }
        post {
            val userId = call.principal<JWTPrincipal>()?.userId() ?: error("Unauthenticated request")
            call.respond(HttpStatusCode.Created, repository.createForUser(userId, call.receive()))
        }
        put("/{profileId}") {
            val userId = call.principal<JWTPrincipal>()?.userId() ?: error("Unauthenticated request")
            val profileId = call.parameters["profileId"] ?: throw ProfileException(
                HttpStatusCode.BadRequest,
                "MISSING_PROFILE_ID",
                "Profile id is required",
            )
            call.respond(repository.updateForUser(userId, profileId, call.receive<UpdateProfileCommand>()))
        }
        delete("/{profileId}") {
            val userId = call.principal<JWTPrincipal>()?.userId() ?: error("Unauthenticated request")
            val profileId = call.parameters["profileId"] ?: throw ProfileException(
                HttpStatusCode.BadRequest,
                "MISSING_PROFILE_ID",
                "Profile id is required",
            )
            repository.deleteForUser(userId, profileId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
