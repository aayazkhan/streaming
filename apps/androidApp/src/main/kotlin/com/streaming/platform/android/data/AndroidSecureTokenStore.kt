package com.streaming.platform.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.streaming.platform.security.SecureTokenStore
import com.streaming.platform.security.TokenPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Keystore-backed implementation of the shared [SecureTokenStore] contract, via
 * [EncryptedSharedPreferences] (AES-256-GCM, key held in the Android Keystore). Unlike the web/admin
 * clients — which had to accept a real tradeoff (access token in memory only, refresh token in
 * sessionStorage, no httpOnly-cookie equivalent available in a browser) — Android has a genuine
 * secure-at-rest option, so both tokens are stored here without that compromise.
 */
class AndroidSecureTokenStore(context: Context) : SecureTokenStore {
    private val json = Json { ignoreUnknownKeys = true }

    private val preferences: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun read(): TokenPair? = withContext(Dispatchers.IO) {
        preferences.getString(KEY_TOKENS, null)?.let { runCatching { json.decodeFromString<TokenPair>(it) }.getOrNull() }
    }

    override suspend fun write(tokens: TokenPair) = withContext(Dispatchers.IO) {
        preferences.edit().putString(KEY_TOKENS, json.encodeToString(tokens)).apply()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        preferences.edit().remove(KEY_TOKENS).apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "streaming_secure_tokens"
        const val KEY_TOKENS = "tokens"
    }
}
