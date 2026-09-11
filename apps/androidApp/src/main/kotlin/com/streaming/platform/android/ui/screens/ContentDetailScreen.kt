package com.streaming.platform.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.streaming.platform.android.ui.ContentDetailUiState
import com.streaming.platform.android.ui.ContentDetailViewModel
import com.streaming.platform.android.ui.components.ErrorMessage
import com.streaming.platform.android.ui.components.LoadingSpinner

@Composable
fun ContentDetailScreen(viewModel: ContentDetailViewModel, onPlayClick: (String) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is ContentDetailUiState.Loading -> LoadingSpinner(modifier = Modifier.fillMaxSize())
        is ContentDetailUiState.Error -> ErrorMessage(state.message, modifier = Modifier.fillMaxSize())
        is ContentDetailUiState.Loaded -> {
            val summary = state.detail.summary
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                Text(summary.title, style = MaterialTheme.typography.headlineMedium)
                Text(
                    listOfNotNull(
                        summary.releaseYear?.toString(),
                        state.detail.genres.joinToString(", ").ifEmpty { null },
                        summary.rating?.let { "★ %.1f".format(it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(summary.synopsis ?: "", modifier = Modifier.padding(top = 12.dp))
                Row(modifier = Modifier.padding(top = 16.dp)) {
                    Button(
                        onClick = { onPlayClick(summary.id) },
                        enabled = state.detail.playable,
                    ) {
                        Text(if (state.detail.playable) "Play" else "Unavailable")
                    }
                    OutlinedButton(
                        onClick = { viewModel.toggleWatchlist() },
                        enabled = !state.isUpdatingWatchlist,
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Text(if (state.isInWatchlist) "Remove from My List" else "Add to My List")
                    }
                }
                if (state.detail.credits.isNotEmpty()) {
                    Text(
                        "Cast: " + state.detail.credits.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                    )
                }
            }
        }
    }
}
