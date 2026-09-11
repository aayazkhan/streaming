package com.streaming.platform.identity

import com.streaming.platform.common.UserId
import kotlinx.datetime.Instant
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

data class StoredUser(
    val id: UserId,
    val email: String,
    val displayName: String,
    val passwordHash: String,
    val emailVerified: Boolean,
    val createdAt: Instant,
    val role: UserRole = UserRole.USER,
)

enum class UserRole { USER, ADMIN }

data class StoredRefreshToken(
    val userId: UserId,
    val deviceId: String,
    val expiresAt: Instant,
    val revokedAt: Instant?,
)

interface IdentityRepository {
    fun findUserByEmail(email: String): StoredUser?
    fun findUserById(userId: UserId): StoredUser?
    fun insertUser(user: StoredUser): Boolean
    fun updateUserRole(userId: UserId, role: UserRole): Boolean
    fun findRefreshToken(tokenHash: String): StoredRefreshToken?
    fun insertRefreshToken(userId: UserId, deviceId: String, tokenHash: String, expiresAt: Instant)
    fun revokeRefreshToken(tokenHash: String, revokedAt: Instant): Boolean
}

class JdbcIdentityRepository(private val dataSource: DataSource) : IdentityRepository {
    override fun findUserByEmail(email: String): StoredUser? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT id, email, display_name, password_hash, email_verified, created_at, role FROM users WHERE email = ?",
        ).use { statement ->
            statement.setString(1, email)
            statement.executeQuery().use { result -> if (result.next()) result.toUser() else null }
        }
    }

    override fun findUserById(userId: UserId): StoredUser? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT id, email, display_name, password_hash, email_verified, created_at, role FROM users WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, UUID.fromString(userId))
            statement.executeQuery().use { result -> if (result.next()) result.toUser() else null }
        }
    }

    override fun insertUser(user: StoredUser): Boolean = try {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO users(id, email, display_name, password_hash, email_verified, role, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setObject(1, UUID.fromString(user.id))
                statement.setString(2, user.email)
                statement.setString(3, user.displayName)
                statement.setString(4, user.passwordHash)
                statement.setBoolean(5, user.emailVerified)
                statement.setString(6, user.role.name)
                statement.setObject(7, OffsetDateTime.parse(user.createdAt.toString()))
                statement.setObject(8, OffsetDateTime.parse(user.createdAt.toString()))
                statement.executeUpdate() == 1
            }
        }
    } catch (_: SQLException) {
        false
    }

    override fun updateUserRole(userId: UserId, role: UserRole): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("UPDATE users SET role = ?, updated_at = ? WHERE id = ?").use { statement ->
            statement.setString(1, role.name)
            statement.setObject(2, OffsetDateTime.now())
            statement.setObject(3, UUID.fromString(userId))
            statement.executeUpdate() == 1
        }
    }

    override fun findRefreshToken(tokenHash: String): StoredRefreshToken? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT user_id, device_id, expires_at, revoked_at FROM refresh_tokens WHERE token_hash = ?",
        ).use { statement ->
            statement.setString(1, tokenHash)
            statement.executeQuery().use { result -> if (result.next()) result.toRefreshToken() else null }
        }
    }

    override fun insertRefreshToken(userId: UserId, deviceId: String, tokenHash: String, expiresAt: Instant) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO refresh_tokens(id, user_id, device_id, token_hash, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                val now = OffsetDateTime.now()
                statement.setObject(1, UUID.randomUUID())
                statement.setObject(2, UUID.fromString(userId))
                statement.setString(3, deviceId)
                statement.setString(4, tokenHash)
                statement.setObject(5, OffsetDateTime.parse(expiresAt.toString()))
                statement.setObject(6, now)
                statement.executeUpdate()
            }
        }
    }

    override fun revokeRefreshToken(tokenHash: String, revokedAt: Instant): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "UPDATE refresh_tokens SET revoked_at = ? WHERE token_hash = ? AND revoked_at IS NULL",
        ).use { statement ->
            statement.setObject(1, OffsetDateTime.parse(revokedAt.toString()))
            statement.setString(2, tokenHash)
            statement.executeUpdate() == 1
        }
    }

    private fun ResultSet.toUser() = StoredUser(
        id = getObject("id", UUID::class.java).toString(),
        email = getString("email"),
        displayName = getString("display_name"),
        passwordHash = getString("password_hash"),
        emailVerified = getBoolean("email_verified"),
        createdAt = Instant.parse(getObject("created_at", OffsetDateTime::class.java).toInstant().toString()),
        role = runCatching { UserRole.valueOf(getString("role")) }.getOrDefault(UserRole.USER),
    )

    private fun ResultSet.toRefreshToken() = StoredRefreshToken(
        userId = getObject("user_id", UUID::class.java).toString(),
        deviceId = getString("device_id"),
        expiresAt = Instant.parse(getObject("expires_at", OffsetDateTime::class.java).toInstant().toString()),
        revokedAt = getObject("revoked_at", OffsetDateTime::class.java)?.let {
            Instant.parse(it.toInstant().toString())
        },
    )
}
