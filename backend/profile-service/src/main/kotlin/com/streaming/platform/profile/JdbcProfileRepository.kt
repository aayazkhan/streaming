package com.streaming.platform.profile

import com.streaming.platform.common.ProfileId
import com.streaming.platform.common.UserId
import com.streaming.platform.profile.ProfileKind
import com.streaming.platform.profile.Profile
import com.streaming.platform.profile.CreateProfileCommand
import com.streaming.platform.profile.UpdateProfileCommand
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Instant
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

class JdbcProfileRepository(private val dataSource: DataSource) {

    fun listForUser(userId: UserId): List<Profile> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT id, user_id, name, kind, language, subtitle_language, avatar_key, created_at, updated_at FROM profiles WHERE user_id = ? ORDER BY created_at ASC",
        ).use { statement ->
            statement.setObject(1, UUID.fromString(userId))
            statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toProfile()) } }
        }
    }

    fun createForUser(userId: UserId, command: CreateProfileCommand): Profile {
        validateName(command.name)
        val profile = newProfile(userId, command)
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO profiles(id, user_id, name, kind, language, subtitle_language, avatar_key, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                val timestamp = OffsetDateTime.parse(profile.createdAt.toString())
                statement.setObject(1, UUID.fromString(profile.id))
                statement.setObject(2, UUID.fromString(userId))
                statement.setString(3, profile.name)
                statement.setString(4, profile.kind.name)
                statement.setString(5, profile.language)
                statement.setString(6, profile.subtitleLanguage)
                statement.setString(7, profile.avatarKey)
                statement.setObject(8, timestamp)
                statement.setObject(9, timestamp)
                statement.executeUpdate()
            }
        }
        return profile
    }

    fun updateForUser(userId: UserId, profileId: ProfileId, command: UpdateProfileCommand): Profile {
        validateName(command.name)
        val now = kotlinx.datetime.Clock.System.now()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE profiles SET name = ?, language = ?, subtitle_language = ?, avatar_key = ?, updated_at = ? WHERE id = ? AND user_id = ?",
            ).use { statement ->
                statement.setString(1, command.name.trim())
                statement.setString(2, command.language)
                statement.setString(3, command.subtitleLanguage)
                statement.setString(4, command.avatarKey)
                statement.setObject(5, OffsetDateTime.parse(now.toString()))
                statement.setObject(6, UUID.fromString(profileId))
                statement.setObject(7, UUID.fromString(userId))
                if (statement.executeUpdate() != 1) throw notFound()
            }
        }
        return findForUser(userId, profileId) ?: throw notFound()
    }

    fun deleteForUser(userId: UserId, profileId: ProfileId) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM profiles WHERE id = ? AND user_id = ?").use { statement ->
                statement.setObject(1, UUID.fromString(profileId))
                statement.setObject(2, UUID.fromString(userId))
                if (statement.executeUpdate() != 1) throw notFound()
            }
        }
    }

    private fun findForUser(userId: UserId, profileId: ProfileId): Profile? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT id, user_id, name, kind, language, subtitle_language, avatar_key, created_at, updated_at FROM profiles WHERE id = ? AND user_id = ?",
        ).use { statement ->
            statement.setObject(1, UUID.fromString(profileId))
            statement.setObject(2, UUID.fromString(userId))
            statement.executeQuery().use { result -> if (result.next()) result.toProfile() else null }
        }
    }

    private fun newProfile(userId: UserId, command: CreateProfileCommand): Profile {
        val now = kotlinx.datetime.Clock.System.now()
        return Profile(
            id = UUID.randomUUID().toString(),
            userId = userId,
            name = command.name.trim(),
            kind = command.kind,
            language = command.language,
            subtitleLanguage = command.subtitleLanguage,
            avatarKey = command.avatarKey,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun ResultSet.toProfile() = Profile(
        id = getObject("id", UUID::class.java).toString(),
        userId = getObject("user_id", UUID::class.java).toString(),
        name = getString("name"),
        kind = ProfileKind.valueOf(getString("kind")),
        language = getString("language"),
        subtitleLanguage = getString("subtitle_language"),
        avatarKey = getString("avatar_key"),
        createdAt = Instant.parse(getObject("created_at", OffsetDateTime::class.java).toInstant().toString()),
        updatedAt = Instant.parse(getObject("updated_at", OffsetDateTime::class.java).toInstant().toString()),
    )

    private fun validateName(name: String) {
        if (name.trim().length !in 1..40) throw ProfileException(
            HttpStatusCode.BadRequest,
            "INVALID_PROFILE_NAME",
            "Profile name must be 1 to 40 characters",
        )
    }

    private fun notFound() = ProfileException(HttpStatusCode.NotFound, "PROFILE_NOT_FOUND", "Profile was not found")
}
