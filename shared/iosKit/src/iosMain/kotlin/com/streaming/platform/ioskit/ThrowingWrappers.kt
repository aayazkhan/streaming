package com.streaming.platform.ioskit

import com.streaming.platform.authentication.SessionManager
import com.streaming.platform.common.Page
import com.streaming.platform.common.ProfileId
import com.streaming.platform.content.ContentDetail
import com.streaming.platform.content.ContentQuery
import com.streaming.platform.content.ContentRepository
import com.streaming.platform.content.ContentSummary
import com.streaming.platform.content.HomeFeed
import com.streaming.platform.network.AppErrorException
import com.streaming.platform.playback.ContinueWatchingItem
import com.streaming.platform.playback.PlaybackGrant
import com.streaming.platform.playback.PlaybackPosition
import com.streaming.platform.playback.PlaybackRepository
import com.streaming.platform.playback.StartPlaybackCommand
import com.streaming.platform.playback.UpdatePositionCommand
import com.streaming.platform.profile.CreateProfileCommand
import com.streaming.platform.profile.Profile
import com.streaming.platform.profile.ProfileRepository
import com.streaming.platform.search.AutocompleteSuggestion
import com.streaming.platform.search.SearchQuery
import com.streaming.platform.search.SearchRepository
import com.streaming.platform.search.SearchResult
import com.streaming.platform.watchlist.WatchlistEntry
import com.streaming.platform.watchlist.WatchlistRepository

/**
 * Swift-facing throwing wrappers.
 *
 * A Kotlin suspend fun that returns `Result<T>` as a plain *value* (via `.fold`/`Result.failure`)
 * does NOT get bridged into a catchable Swift error — Kotlin/Native's Obj-C/Swift completion-handler
 * bridge only populates the NSError slot when the Kotlin side genuinely throws. Every shared
 * repository method here follows the JVM-idiomatic `Result<T>` return pattern (correct for
 * Android's `.fold()` call sites), so calling them directly from Swift silently discards failures.
 * `Result.getOrThrow()` performs a real throw, which the bridge does convert — these wrappers exist
 * purely so Swift's `try await` sees actual thrown errors instead of silently succeeding.
 *
 * That conversion is itself conditional: Kotlin/Native only converts a genuinely thrown exception
 * into a catchable Swift error when the throwing function is annotated `@Throws(ExceptionType::class)`
 * for that exact type — by default only CancellationException crosses the bridge. Without the
 * annotation the app aborts outright ("Uncaught Kotlin exception ... is considered unexpected and
 * unhandled" — confirmed via a real crash log), so every function here must declare it explicitly.
 */

@Throws(AppErrorException::class, kotlin.coroutines.cancellation.CancellationException::class)
suspend fun SessionManager.loginOrThrow(email: String, password: String) = login(email, password).getOrThrow()

@Throws(AppErrorException::class, kotlin.coroutines.cancellation.CancellationException::class)
suspend fun SessionManager.registerOrThrow(email: String, password: String, displayName: String) =
    register(email, password, displayName).getOrThrow()

@Throws(AppErrorException::class, kotlin.coroutines.cancellation.CancellationException::class)
suspend fun ProfileRepository.listProfilesOrThrow(): List<Profile> = listProfiles().getOrThrow()

@Throws(AppErrorException::class, kotlin.coroutines.cancellation.CancellationException::class)
suspend fun ProfileRepository.createProfileOrThrow(command: CreateProfileCommand): Profile = createProfile(command).getOrThrow()

@Throws(AppErrorException::class, kotlin.coroutines.cancellation.CancellationException::class)
suspend fun ContentRepository.findByIdOrThrow(contentId: String): ContentDetail = findById(contentId).getOrThrow()

@Throws(AppErrorException::class, kotlin.coroutines.cancellation.CancellationException::class)
suspend fun ContentRepository.listOrThrow(query: ContentQuery): Page<ContentSummary> = list(query).getOrThrow()

@Throws(AppErrorException::class, kotlin.coroutines.cancellation.CancellationException::class)
suspend fun ContentRepository.homeOrThrow(): HomeFeed = home().getOrThrow()

@Throws(AppErrorException::class, kotlin.coroutines.cancellation.CancellationException::class)
suspend fun SearchRepository.searchOrThrow(query: SearchQuery, userId: String?): SearchResult = search(query, userId).getOrThrow()

@Throws(AppErrorException::class, kotlin.coroutines.cancellation.CancellationException::class)
suspend fun SearchRepository.autocompleteOrThrow(prefix: String, limit: Int): List<AutocompleteSuggestion> =
    autocomplete(prefix, limit).getOrThrow()

@Throws(AppErrorException::class, kotlin.coroutines.cancellation.CancellationException::class)
suspend fun WatchlistRepository.listOrThrow(userId: String, limit: Int, cursor: String?): Page<WatchlistEntry> =
    list(userId, limit, cursor).getOrThrow()

@Throws(AppErrorException::class, kotlin.coroutines.cancellation.CancellationException::class)
suspend fun WatchlistRepository.addOrThrow(userId: String, contentId: String): WatchlistEntry = add(userId, contentId).getOrThrow()

@Throws(AppErrorException::class, kotlin.coroutines.cancellation.CancellationException::class)
suspend fun WatchlistRepository.removeOrThrow(userId: String, contentId: String) = remove(userId, contentId).getOrThrow()

@Throws(AppErrorException::class, kotlin.coroutines.cancellation.CancellationException::class)
suspend fun PlaybackRepository.startOrThrow(command: StartPlaybackCommand, userId: String): PlaybackGrant =
    start(command, userId).getOrThrow()

@Throws(AppErrorException::class, kotlin.coroutines.cancellation.CancellationException::class)
suspend fun PlaybackRepository.updatePositionOrThrow(sessionId: String, userId: String, command: UpdatePositionCommand): PlaybackPosition =
    updatePosition(sessionId, userId, command).getOrThrow()

@Throws(AppErrorException::class, kotlin.coroutines.cancellation.CancellationException::class)
suspend fun PlaybackRepository.continueWatchingOrThrow(userId: String, profileId: ProfileId?, limit: Int): List<ContinueWatchingItem> =
    continueWatching(userId, profileId, limit).getOrThrow()
