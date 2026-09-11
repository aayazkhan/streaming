package com.streaming.platform.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streaming.platform.content.ContentQuery
import com.streaming.platform.content.ContentRepository
import com.streaming.platform.content.ContentSummary
import com.streaming.platform.content.ContentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrowseUiState(
    val items: List<ContentSummary> = emptyList(),
    val selectedType: ContentType? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
    private val cursor: String? = null,
) {
    val nextCursor: String? get() = cursor
}

class BrowseViewModel(private val contentRepository: ContentRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    init { load() }

    fun selectType(type: ContentType?) {
        _uiState.value = BrowseUiState(selectedType = type)
        load()
    }

    fun load() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            contentRepository.list(ContentQuery(type = state.selectedType, limit = 24)).fold(
                onSuccess = { page ->
                    _uiState.value = BrowseUiState(
                        items = page.items,
                        selectedType = state.selectedType,
                        isLoading = false,
                        hasMore = page.hasMore,
                        cursor = page.nextCursor,
                    )
                },
                onFailure = { _uiState.value = state.copy(isLoading = false, error = it.readableMessage()) },
            )
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val cursor = state.nextCursor ?: return
        if (state.isLoadingMore || !state.hasMore) return
        viewModelScope.launch {
            _uiState.value = state.copy(isLoadingMore = true)
            contentRepository.list(ContentQuery(type = state.selectedType, cursor = cursor, limit = 24)).fold(
                onSuccess = { page ->
                    _uiState.value = state.copy(
                        items = state.items + page.items,
                        isLoadingMore = false,
                        hasMore = page.hasMore,
                        cursor = page.nextCursor,
                    )
                },
                onFailure = { _uiState.value = state.copy(isLoadingMore = false, error = it.readableMessage()) },
            )
        }
    }
}
