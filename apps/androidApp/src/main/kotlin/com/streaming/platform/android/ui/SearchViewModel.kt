package com.streaming.platform.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streaming.platform.content.ContentSummary
import com.streaming.platform.search.SearchQuery
import com.streaming.platform.search.SearchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<ContentSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class SearchViewModel(private val searchRepository: SearchRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var debounceJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        debounceJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(results = emptyList(), isLoading = false)
            return
        }
        debounceJob = viewModelScope.launch {
            delay(300)
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            searchRepository.search(SearchQuery(query = query), userId = null).fold(
                onSuccess = { result -> _uiState.value = _uiState.value.copy(results = result.items, isLoading = false) },
                onFailure = { _uiState.value = _uiState.value.copy(isLoading = false, error = it.readableMessage()) },
            )
        }
    }
}
