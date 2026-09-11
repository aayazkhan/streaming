package com.streaming.platform.android.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.streaming.platform.android.ui.HomeUiState
import com.streaming.platform.android.ui.HomeViewModel
import com.streaming.platform.android.ui.components.ContentRow
import com.streaming.platform.android.ui.components.ErrorMessage
import com.streaming.platform.android.ui.components.LoadingSpinner

@Composable
fun HomeScreen(viewModel: HomeViewModel, onContentClick: (String) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is HomeUiState.Loading -> LoadingSpinner(modifier = Modifier.fillMaxSize())
        is HomeUiState.Error -> ErrorMessage(state.message, modifier = Modifier.fillMaxSize())
        is HomeUiState.Loaded -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (state.continueWatching.isNotEmpty()) {
                item {
                    ContentRow("Continue watching", state.continueWatching.map { it.content }, onContentClick)
                }
            }
            items(state.feed.sections, key = { it.key }) { section ->
                ContentRow(section.title, section.items, onContentClick)
            }
            if (state.feed.sections.isEmpty() && state.continueWatching.isEmpty()) {
                item { Text("No content is available yet.", modifier = Modifier.fillMaxSize()) }
            }
        }
    }
}
