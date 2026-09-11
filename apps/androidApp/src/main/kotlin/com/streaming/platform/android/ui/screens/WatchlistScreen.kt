package com.streaming.platform.android.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.streaming.platform.android.ui.WatchlistUiState
import com.streaming.platform.android.ui.WatchlistViewModel
import com.streaming.platform.android.ui.components.ContentCard
import com.streaming.platform.android.ui.components.ErrorMessage
import com.streaming.platform.android.ui.components.LoadingSpinner

@Composable
fun WatchlistScreen(viewModel: WatchlistViewModel, onContentClick: (String) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is WatchlistUiState.Loading -> LoadingSpinner(modifier = Modifier.fillMaxSize())
        is WatchlistUiState.Error -> ErrorMessage(state.message, modifier = Modifier.fillMaxSize())
        is WatchlistUiState.Loaded -> if (state.items.isEmpty()) {
            Text("You haven't added any titles yet.", modifier = Modifier.padding(16.dp))
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(16.dp)) {
                items(state.items, key = { it.content.id }) { entry ->
                    ContentCard(entry.content, onClick = { onContentClick(entry.content.id) }, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}
