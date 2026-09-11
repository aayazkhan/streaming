package com.streaming.platform.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streaming.platform.content.ContentDetail
import com.streaming.platform.content.ContentRepository
import com.streaming.platform.watchlist.WatchlistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ContentDetailUiState {
    data object Loading : ContentDetailUiState
    data class Error(val message: String) : ContentDetailUiState
    data class Loaded(val detail: ContentDetail, val isInWatchlist: Boolean, val isUpdatingWatchlist: Boolean = false) : ContentDetailUiState
}

class ContentDetailViewModel(
    private val contentId: String,
    private val contentRepository: ContentRepository,
    private val watchlistRepository: WatchlistRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ContentDetailUiState>(ContentDetailUiState.Loading)
    val uiState: StateFlow<ContentDetailUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = ContentDetailUiState.Loading
            val detailResult = contentRepository.findById(contentId)
            val watchlist = watchlistRepository.list("", 50, null).getOrNull()
            detailResult.fold(
                onSuccess = { detail ->
                    val isInList = watchlist?.items?.any { it.content.id == contentId } ?: false
                    _uiState.value = ContentDetailUiState.Loaded(detail, isInList)
                },
                onFailure = { _uiState.value = ContentDetailUiState.Error(it.readableMessage()) },
            )
        }
    }

    fun toggleWatchlist() {
        val current = _uiState.value as? ContentDetailUiState.Loaded ?: return
        _uiState.value = current.copy(isUpdatingWatchlist = true)
        viewModelScope.launch {
            val result = if (current.isInWatchlist) watchlistRepository.remove("", contentId) else watchlistRepository.add("", contentId)
            result.fold(
                onSuccess = { _uiState.value = current.copy(isInWatchlist = !current.isInWatchlist, isUpdatingWatchlist = false) },
                onFailure = { _uiState.value = current.copy(isUpdatingWatchlist = false) },
            )
        }
    }
}
