package com.streaming.platform.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streaming.platform.android.auth.ActiveProfileHolder
import com.streaming.platform.profile.CreateProfileCommand
import com.streaming.platform.profile.Profile
import com.streaming.platform.profile.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Error(val message: String) : ProfileUiState
    data class Loaded(val profiles: List<Profile>) : ProfileUiState
}

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val activeProfileHolder: ActiveProfileHolder,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    val activeProfileId: StateFlow<String?> = activeProfileHolder.activeProfileId

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            profileRepository.listProfiles().fold(
                onSuccess = { _uiState.value = ProfileUiState.Loaded(it) },
                onFailure = { _uiState.value = ProfileUiState.Error(it.readableMessage()) },
            )
        }
    }

    fun selectProfile(profileId: String) {
        activeProfileHolder.set(profileId)
    }

    fun createProfile(name: String, onCreated: (String) -> Unit) {
        if (name.isBlank()) return
        _isCreating.value = true
        viewModelScope.launch {
            profileRepository.createProfile(CreateProfileCommand(name = name.trim())).fold(
                onSuccess = { profile ->
                    activeProfileHolder.set(profile.id)
                    onCreated(profile.id)
                },
                onFailure = { _uiState.value = ProfileUiState.Error(it.readableMessage()) },
            )
            _isCreating.value = false
        }
    }
}
