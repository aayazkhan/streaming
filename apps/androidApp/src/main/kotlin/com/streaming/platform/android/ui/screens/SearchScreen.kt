package com.streaming.platform.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.streaming.platform.android.ui.SearchViewModel
import com.streaming.platform.android.ui.components.ContentCard
import com.streaming.platform.android.ui.components.LoadingSpinner

@Composable
fun SearchScreen(viewModel: SearchViewModel, onContentClick: (String) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("Search titles...") },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        if (uiState.isLoading) {
            LoadingSpinner(modifier = Modifier.fillMaxSize())
        } else if (uiState.results.isEmpty() && uiState.query.isNotBlank()) {
            Text("No titles matched your search.", modifier = Modifier.padding(16.dp))
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(16.dp)) {
                items(uiState.results, key = { it.id }) { item ->
                    ContentCard(item, onClick = { onContentClick(item.id) }, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}
