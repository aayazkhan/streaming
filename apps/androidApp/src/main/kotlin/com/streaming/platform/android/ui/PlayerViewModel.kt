package com.streaming.platform.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streaming.platform.android.auth.ActiveProfileHolder
import com.streaming.platform.playback.PlaybackGrant
import com.streaming.platform.playback.PlaybackRepository
import com.streaming.platform.playback.StartPlaybackCommand
import com.streaming.platform.playback.UpdatePositionCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Error(val message: String) : PlayerUiState
    data class Ready(val grant: PlaybackGrant) : PlayerUiState
}

class PlayerViewModel(
    private val contentId: String,
    private val playbackRepository: PlaybackRepository,
    activeProfileHolder: ActiveProfileHolder,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val profileId = activeProfileHolder.activeProfileId.value

    init {
        val activeProfileId = profileId
        if (activeProfileId == null) {
            _uiState.value = PlayerUiState.Error("No profile selected.")
        } else {
            viewModelScope.launch {
                playbackRepository.start(StartPlaybackCommand(contentId, activeProfileId), userId = "").fold(
                    onSuccess = { grant -> _uiState.value = PlayerUiState.Ready(grant) },
                    onFailure = { _uiState.value = PlayerUiState.Error(it.readableMessage()) },
                )
            }
        }
    }

    fun reportPosition(positionSeconds: Long, durationSeconds: Long?, completed: Boolean = false) {
        val state = _uiState.value as? PlayerUiState.Ready ?: return
        viewModelScope.launch {
            playbackRepository.updatePosition(
                state.grant.session.id,
                userId = "",
                UpdatePositionCommand(positionSeconds, durationSeconds, completed),
            )
        }
    }
}
