package com.streaming.platform.content

import com.streaming.platform.common.ContentId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class ContentType { MOVIE, SERIES, SEASON, EPISODE, TRAILER, SHORT }

@Serializable
enum class ContentStatus { DRAFT, PUBLISHED, ARCHIVED }

@Serializable
enum class AccessTier { FREE, PREMIUM }

@Serializable
data class PersonCredit(
    val name: String,
    val role: String,
    val character: String? = null,
)

@Serializable
data class SubtitleTrack(
    val language: String,
    val label: String,
    val uri: String? = null,
)

@Serializable
data class AudioTrack(
    val language: String,
    val label: String,
    val isDefault: Boolean = false,
)

@Serializable
data class ContentSummary(
    val id: ContentId,
    val type: ContentType,
    val title: String,
    val synopsis: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseYear: Int?,
    val durationSeconds: Int?,
    val accessTier: AccessTier,
    val rating: Double?,
    val createdAt: Instant,
)

@Serializable
data class ContentDetail(
    val summary: ContentSummary,
    val genres: List<String>,
    val credits: List<PersonCredit>,
    val audioTracks: List<AudioTrack>,
    val subtitleTracks: List<SubtitleTrack>,
    val children: List<ContentSummary>,
    val playable: Boolean,
)

@Serializable
data class ContentQuery(
    val type: ContentType? = null,
    val genre: String? = null,
    val releaseYear: Int? = null,
    val cursor: String? = null,
    val limit: Int = 20,
)

@Serializable
data class ContentSection(
    val key: String,
    val title: String,
    val items: List<ContentSummary>,
)

@Serializable
data class HomeFeed(
    val sections: List<ContentSection>,
)

@Serializable
data class CreateContentCommand(
    val type: ContentType,
    val title: String,
    val synopsis: String? = null,
    val releaseYear: Int? = null,
    val durationSeconds: Int? = null,
    val accessTier: AccessTier = AccessTier.FREE,
    val status: ContentStatus = ContentStatus.DRAFT,
    val isFeatured: Boolean = false,
    val genreNames: List<String> = emptyList(),
)

@Serializable
data class UpdateContentCommand(
    val title: String,
    val synopsis: String? = null,
    val releaseYear: Int? = null,
    val durationSeconds: Int? = null,
    val accessTier: AccessTier,
    val status: ContentStatus,
    val isFeatured: Boolean = false,
    val genreNames: List<String> = emptyList(),
)

@Serializable
data class AdminContentSummary(
    val summary: ContentSummary,
    val status: ContentStatus,
)

@Serializable
data class Genre(
    val id: String,
    val name: String,
)

interface ContentRepository {
    suspend fun findById(contentId: ContentId): Result<ContentDetail>
    suspend fun list(query: ContentQuery): Result<com.streaming.platform.common.Page<ContentSummary>>
    suspend fun home(): Result<HomeFeed>
    suspend fun similar(contentId: ContentId, limit: Int): Result<List<ContentSummary>>
}
