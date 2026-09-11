package com.streaming.platform.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.streaming.platform.android.di.AppContainer

/**
 * Manual ViewModel factory keyed off [AppContainer] — consistent with the app's manual-DI choice
 * (no Hilt), and small enough that a generated factory would be more machinery than the four
 * ViewModel classes it serves.
 */
class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return when (modelClass) {
            AuthViewModel::class.java -> AuthViewModel(container.sessionManager) as T
            HomeViewModel::class.java -> HomeViewModel(container.contentRepository, container.playbackRepository, container.activeProfileHolder) as T
            ProfileViewModel::class.java -> ProfileViewModel(container.profileRepository, container.activeProfileHolder) as T
            BrowseViewModel::class.java -> BrowseViewModel(container.contentRepository) as T
            SearchViewModel::class.java -> SearchViewModel(container.searchRepository) as T
            WatchlistViewModel::class.java -> WatchlistViewModel(container.watchlistRepository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
    }
}

class ContentDetailViewModelFactory(
    private val container: AppContainer,
    private val contentId: String,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return ContentDetailViewModel(contentId, container.contentRepository, container.watchlistRepository) as T
    }
}

class PlayerViewModelFactory(
    private val container: AppContainer,
    private val contentId: String,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return PlayerViewModel(contentId, container.playbackRepository, container.activeProfileHolder) as T
    }
}
