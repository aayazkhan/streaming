package com.streaming.platform.android.ui.screens

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.streaming.platform.android.playback.Media3PlaybackAdapter
import com.streaming.platform.android.ui.PlayerUiState
import com.streaming.platform.android.ui.PlayerViewModel
import com.streaming.platform.android.ui.components.ErrorMessage
import com.streaming.platform.android.ui.components.LoadingSpinner
import com.streaming.platform.playback.PlaybackGrant
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(viewModel: PlayerViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is PlayerUiState.Loading -> LoadingSpinner(modifier = Modifier.fillMaxSize())
        is PlayerUiState.Error -> ErrorMessage(state.message, modifier = Modifier.fillMaxSize())
        is PlayerUiState.Ready -> PlayerSurface(state.grant, onPosition = viewModel::reportPosition)
    }
}

@Composable
private fun PlayerSurface(grant: PlaybackGrant, onPosition: (Long, Long?, Boolean) -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    val adapter = remember(exoPlayer) { Media3PlaybackAdapter(exoPlayer) }

    DisposableEffect(grant.session.id) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(grant.session.id) {
        adapter.prepare(grant)
        adapter.play()
    }

    LaunchedEffect(grant.session.id) {
        while (true) {
            delay(15_000)
            val position = exoPlayer.currentPosition / 1000
            val duration = exoPlayer.duration.takeIf { it > 0 }?.let { it / 1000 }
            onPosition(position, duration, false)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                setControllerShowTimeoutMs(0)
            }
        },
    )
}
