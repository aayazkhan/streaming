package com.streaming.platform.android.auth

import android.content.Context
import java.util.UUID

/**
 * Stable per-install identifier used to scope refresh-token rotation — not a secret, so plain
 * (unencrypted) SharedPreferences is fine, same tradeoff apps/webApp makes with localStorage.
 * Must be persisted, not regenerated per-process, or every relaunch would fail the server's
 * refresh-token device-match check ("DEVICE_MISMATCH") and force a fresh login every time.
 */
fun getOrCreateDeviceId(context: Context): String {
    val preferences = context.getSharedPreferences("streaming_device", Context.MODE_PRIVATE)
    preferences.getString(KEY_DEVICE_ID, null)?.let { return it }
    val created = UUID.randomUUID().toString()
    preferences.edit().putString(KEY_DEVICE_ID, created).apply()
    return created
}

private const val KEY_DEVICE_ID = "device_id"
