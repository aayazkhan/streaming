package com.streaming.platform.identity

import com.streaming.platform.authentication.LoginCommand
import com.streaming.platform.authentication.RegisterCommand
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdentityServiceTest {
    private val repository = InMemoryIdentityRepository()
    private val issuer = TokenIssuer(
        TokenIssuerConfig(
            issuer = "test-issuer",
            audience = "test-audience",
            secret = "test-secret-that-is-at-least-32-characters",
        ),
    )
    private val service = IdentityService(repository, DeterministicPasswordHasher(), issuer)

    @Test
    fun registrationStoresOnlyHashedPasswordAndIssuesSession() {
        val response = service.register(
            RegisterCommand("user@example.com", "long-enough-password", "User", "device-a"),
        )

        assertEquals("user@example.com", response.user.email)
        assertTrue(response.session.tokens.accessToken.isNotBlank())
        assertTrue(repository.user!!.passwordHash.startsWith("hashed:"))
        assertNotEquals("long-enough-password", repository.user!!.passwordHash)
    }

    @Test
    fun refreshRotationRejectsThePreviousToken() {
        val registered = service.register(
            RegisterCommand("user@example.com", "long-enough-password", "User", "device-a"),
        )
        val rotated = service.refresh(registered.session.tokens.refreshToken, "device-a")

        assertNotEquals(registered.session.tokens.refreshToken, rotated.refreshToken)
        assertFailsWith<IdentityException> {
            service.refresh(registered.session.tokens.refreshToken, "device-a")
        }
    }

    @Test
    fun refreshRejectsDifferentDevice() {
        val registered = service.register(
            RegisterCommand("user@example.com", "long-enough-password", "User", "device-a"),
        )

        val error = assertFailsWith<IdentityException> {
            service.refresh(registered.session.tokens.refreshToken, "device-b")
        }

        assertEquals("DEVICE_MISMATCH", error.code)
    }

    @Test
    fun promoteToAdminUpdatesRoleForExistingUser() {
        service.register(RegisterCommand("user@example.com", "long-enough-password", "User", "device-a"))

        service.promoteToAdmin("user@example.com")

        assertEquals(UserRole.ADMIN, repository.user!!.role)
    }

    @Test
    fun promoteToAdminRejectsUnknownEmail() {
        val error = assertFailsWith<IdentityException> {
            service.promoteToAdmin("nobody@example.com")
        }

        assertEquals("USER_NOT_FOUND", error.code)
    }

    private class DeterministicPasswordHasher : PasswordHasher {
        override fun hash(password: String): String = "hashed:$password"
        override fun verify(encodedHash: String, password: String): Boolean = encodedHash == "hashed:$password"
    }

    private class InMemoryIdentityRepository : IdentityRepository {
        var user: StoredUser? = null
        private val refreshTokens = mutableMapOf<String, StoredRefreshToken>()

        override fun findUserByEmail(email: String): StoredUser? = user?.takeIf { it.email == email }
        override fun findUserById(userId: String): StoredUser? = user?.takeIf { it.id == userId }
        override fun insertUser(user: StoredUser): Boolean {
            if (this.user != null) return false
            this.user = user
            return true
        }
        override fun updateUserRole(userId: String, role: UserRole): Boolean {
            val current = user?.takeIf { it.id == userId } ?: return false
            user = current.copy(role = role)
            return true
        }
        override fun findRefreshToken(tokenHash: String): StoredRefreshToken? = refreshTokens[tokenHash]
        override fun insertRefreshToken(userId: String, deviceId: String, tokenHash: String, expiresAt: Instant) {
            refreshTokens[tokenHash] = StoredRefreshToken(userId, deviceId, expiresAt, null)
        }
        override fun revokeRefreshToken(tokenHash: String, revokedAt: Instant): Boolean {
            val current = refreshTokens[tokenHash] ?: return false
            if (current.revokedAt != null) return false
            refreshTokens[tokenHash] = current.copy(revokedAt = revokedAt)
            return true
        }
    }
}
