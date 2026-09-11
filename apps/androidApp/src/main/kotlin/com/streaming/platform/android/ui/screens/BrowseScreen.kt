package com.streaming.platform.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.streaming.platform.android.ui.BrowseViewModel
import com.streaming.platform.android.ui.components.ContentCard
import com.streaming.platform.android.ui.components.ErrorMessage
import com.streaming.platform.android.ui.components.LoadingSpinner
import com.streaming.platform.content.ContentType

private val TYPES = listOf(null, ContentType.MOVIE, ContentType.SERIES)

@Composable
fun BrowseScreen(viewModel: BrowseViewModel, onContentClick: (String) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(16.dp)) {
            TYPES.forEach { type ->
                FilterChip(
                    selected = uiState.selectedType == type,
                    onClick = { viewModel.selectType(type) },
                    label = { Text(type?.name ?: "All") },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        if (uiState.isLoading) {
            LoadingSpinner(modifier = Modifier.fillMaxSize())
        } else if (uiState.error != null) {
            ErrorMessage(uiState.error ?: "", modifier = Modifier.fillMaxSize())
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(16.dp)) {
                items(uiState.items, key = { it.id }) { item ->
                    ContentCard(item, onClick = { onContentClick(item.id) }, modifier = Modifier.padding(4.dp))
                }
                if (uiState.hasMore) {
                    item {
                        LaunchedEffect(uiState.items.size) { viewModel.loadMore() }
                    }
                }
            }
        }
    }
}
