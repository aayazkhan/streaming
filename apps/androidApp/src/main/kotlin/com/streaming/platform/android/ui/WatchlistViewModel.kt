package com.streaming.platform.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streaming.platform.watchlist.WatchlistEntry
import com.streaming.platform.watchlist.WatchlistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WatchlistUiState {
    data object Loading : WatchlistUiState
    data class Error(val message: String) : WatchlistUiState
    data class Loaded(val items: List<WatchlistEntry>) : WatchlistUiState
}

class WatchlistViewModel(private val watchlistRepository: WatchlistRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<WatchlistUiState>(WatchlistUiState.Loading)
    val uiState: StateFlow<WatchlistUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = WatchlistUiState.Loading
            watchlistRepository.list("", 50, null).fold(
                onSuccess = { _uiState.value = WatchlistUiState.Loaded(it.items) },
                onFailure = { _uiState.value = WatchlistUiState.Error(it.readableMessage()) },
            )
        }
    }
}
