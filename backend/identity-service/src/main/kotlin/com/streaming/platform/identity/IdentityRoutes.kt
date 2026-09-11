package com.streaming.platform.identity

import com.streaming.platform.authentication.LoginCommand
import com.streaming.platform.authentication.RegisterCommand
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class RefreshRequest(val refreshToken: String, val deviceId: String)
@Serializable
data class LogoutRequest(val refreshToken: String)
@Serializable
data class AdminBootstrapRequest(val email: String, val secret: String)

fun Route.identityRoutes(service: IdentityService) {
    route("/auth") {
        post("/register") {
            val command = call.receive<RegisterCommand>()
            call.respond(HttpStatusCode.Created, service.register(command))
        }
        post("/login") {
            val command = call.receive<LoginCommand>()
            call.respond(service.login(command))
        }
        post("/refresh") {
            val request = call.receive<RefreshRequest>()
            call.respond(service.refresh(request.refreshToken, request.deviceId))
        }
        post("/logout") {
            service.logout(call.receive<LogoutRequest>().refreshToken)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

/**
 * One-time admin bootstrap, gated by a server-side secret (ADMIN_BOOTSTRAP_SECRET) rather than
 * any hardcoded/fake admin account. If the secret isn't configured the endpoint is disabled
 * (503) rather than silently accepting no secret — forgetting to set it fails closed, not open.
 */
fun Route.adminBootstrapRoutes(service: IdentityService, bootstrapSecret: String?) {
    post("/admin/bootstrap") {
        val configured = bootstrapSecret
        if (configured.isNullOrBlank()) {
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("code" to "BOOTSTRAP_DISABLED", "message" to "ADMIN_BOOTSTRAP_SECRET is not configured"))
            return@post
        }
        val request = call.receive<AdminBootstrapRequest>()
        if (request.secret != configured) {
            call.respond(HttpStatusCode.Forbidden, mapOf("code" to "INVALID_BOOTSTRAP_SECRET", "message" to "Bootstrap secret is incorrect"))
            return@post
        }
        service.promoteToAdmin(request.email)
        call.respond(HttpStatusCode.NoContent)
    }
}
