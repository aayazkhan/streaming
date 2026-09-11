package com.streaming.platform.identity

import com.streaming.platform.authentication.AuthenticationResponse
import com.streaming.platform.authentication.LoginCommand
import com.streaming.platform.authentication.RegisterCommand
import com.streaming.platform.authentication.UserSummary
import com.streaming.platform.security.Session
import com.streaming.platform.security.TokenPair
import io.ktor.http.HttpStatusCode
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

class IdentityService(
    private val repository: IdentityRepository,
    private val passwordHasher: PasswordHasher,
    private val tokenIssuer: TokenIssuer,
) {
    fun register(command: RegisterCommand): AuthenticationResponse {
        validateRegistration(command)
        val email = command.email.trim().lowercase()
        if (repository.findUserByEmail(email) != null) {
            throw IdentityException(HttpStatusCode.Conflict, "EMAIL_IN_USE", "An account already exists for this email")
        }
        val now = kotlinx.datetime.Instant.fromEpochMilliseconds(Instant.now().toEpochMilli())
        val user = StoredUser(
            id = java.util.UUID.randomUUID().toString(),
            email = email,
            displayName = command.displayName.trim(),
            passwordHash = passwordHasher.hash(command.password),
            emailVerified = false,
            createdAt = now,
            role = UserRole.USER,
        )
        if (!repository.insertUser(user)) {
            throw IdentityException(HttpStatusCode.Conflict, "REGISTRATION_CONFLICT", "The account could not be created")
        }
        return createResponse(user, command.deviceId)
    }

    fun login(command: LoginCommand): AuthenticationResponse {
        val user = repository.findUserByEmail(command.email.trim().lowercase())
            ?: throw invalidCredentials()
        if (!passwordHasher.verify(user.passwordHash, command.password)) throw invalidCredentials()
        return createResponse(user, command.deviceId)
    }

    fun refresh(refreshToken: String, deviceId: String): TokenPair {
        if (refreshToken.isBlank()) throw invalidCredentials()
        val hash = hashToken(refreshToken)
        val stored = repository.findRefreshToken(hash) ?: throw invalidCredentials()
        val now = Instant.now()
        if (stored.revokedAt != null || stored.expiresAt <= kotlinx.datetime.Instant.fromEpochMilliseconds(now.toEpochMilli())) {
            throw invalidCredentials()
        }
        if (stored.deviceId != deviceId) {
            throw IdentityException(HttpStatusCode.Unauthorized, "DEVICE_MISMATCH", "This session belongs to another device")
        }
        if (!repository.revokeRefreshToken(hash, kotlinx.datetime.Instant.fromEpochMilliseconds(now.toEpochMilli()))) {
            throw invalidCredentials()
        }
        val role = repository.findUserById(stored.userId)?.role ?: UserRole.USER
        val issued = tokenIssuer.issue(stored.userId, role, now)
        repository.insertRefreshToken(stored.userId, deviceId, hashToken(issued.tokenPair.refreshToken), issued.refreshTokenExpiresAt)
        return issued.tokenPair
    }

    /**
     * One-time bootstrap for the first admin account: promotes an already-registered user to
     * ADMIN. Callers must have already verified the caller presented the correct bootstrap
     * secret — this method itself performs no authorization, that lives in the route.
     */
    fun promoteToAdmin(email: String) {
        val user = repository.findUserByEmail(email.trim().lowercase())
            ?: throw IdentityException(HttpStatusCode.NotFound, "USER_NOT_FOUND", "No account exists for this email")
        if (!repository.updateUserRole(user.id, UserRole.ADMIN)) {
            throw IdentityException(HttpStatusCode.Conflict, "ROLE_UPDATE_FAILED", "Could not update the account role")
        }
    }

    fun logout(refreshToken: String) {
        if (refreshToken.isNotBlank()) {
            repository.revokeRefreshToken(
                hashToken(refreshToken),
                kotlinx.datetime.Instant.fromEpochMilliseconds(Instant.now().toEpochMilli()),
            )
        }
    }

    private fun createResponse(user: StoredUser, deviceId: String): AuthenticationResponse {
        val issued = tokenIssuer.issue(user.id, user.role)
        repository.insertRefreshToken(user.id, deviceId, hashToken(issued.tokenPair.refreshToken), issued.refreshTokenExpiresAt)
        return AuthenticationResponse(
            user = UserSummary(user.id, user.email, user.displayName, user.emailVerified),
            session = Session(user.id, deviceId, issued.tokenPair),
        )
    }

    private fun validateRegistration(command: RegisterCommand) {
        if (!EMAIL_PATTERN.matches(command.email.trim())) {
            throw IdentityException(HttpStatusCode.BadRequest, "INVALID_EMAIL", "A valid email address is required")
        }
        if (command.password.length < 12) {
            throw IdentityException(HttpStatusCode.BadRequest, "WEAK_PASSWORD", "Password must be at least 12 characters")
        }
        if (command.displayName.trim().length !in 1..80) {
            throw IdentityException(HttpStatusCode.BadRequest, "INVALID_DISPLAY_NAME", "Display name must be 1 to 80 characters")
        }
        if (command.deviceId.isBlank() || command.deviceId.length > 200) {
            throw IdentityException(HttpStatusCode.BadRequest, "INVALID_DEVICE", "A valid device identifier is required")
        }
    }

    private fun invalidCredentials() = IdentityException(
        HttpStatusCode.Unauthorized,
        "INVALID_CREDENTIALS",
        "Email or password is incorrect",
    )

    private fun hashToken(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray())
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private companion object {
        val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
