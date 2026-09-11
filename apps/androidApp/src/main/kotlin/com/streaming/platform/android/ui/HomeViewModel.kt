package com.streaming.platform.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streaming.platform.android.auth.ActiveProfileHolder
import com.streaming.platform.content.ContentRepository
import com.streaming.platform.content.HomeFeed
import com.streaming.platform.playback.ContinueWatchingItem
import com.streaming.platform.playback.PlaybackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Loaded(val feed: HomeFeed, val continueWatching: List<ContinueWatchingItem>) : HomeUiState
}

class HomeViewModel(
    private val contentRepository: ContentRepository,
    private val playbackRepository: PlaybackRepository,
    private val activeProfileHolder: ActiveProfileHolder,
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            val feedResult = contentRepository.home()
            val profileId = activeProfileHolder.activeProfileId.value
            val continueWatching = playbackRepository.continueWatching("", profileId, 20).getOrDefault(emptyList())
            feedResult.fold(
                onSuccess = { feed -> _uiState.value = HomeUiState.Loaded(feed, continueWatching) },
                onFailure = { _uiState.value = HomeUiState.Error(it.readableMessage()) },
            )
        }
    }
}
