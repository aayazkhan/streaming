package com.streaming.platform.android.auth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks which profile ("Who's watching?") is active for the current session — mirrors
 * apps/webApp's ActiveProfileContext. Persisted to plain SharedPreferences (not a secret) so a
 * profile choice survives process death, but cleared whenever the session itself is cleared.
 */
class ActiveProfileHolder(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("streaming_active_profile", Context.MODE_PRIVATE)

    private val _activeProfileId = MutableStateFlow(preferences.getString(KEY_PROFILE_ID, null))
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    fun set(profileId: String?) {
        _activeProfileId.value = profileId
        preferences.edit().apply {
            if (profileId != null) putString(KEY_PROFILE_ID, profileId) else remove(KEY_PROFILE_ID)
        }.apply()
    }

    private companion object {
        const val KEY_PROFILE_ID = "profile_id"
    }
}
