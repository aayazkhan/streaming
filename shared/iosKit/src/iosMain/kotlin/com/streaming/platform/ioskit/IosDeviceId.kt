package com.streaming.platform.ioskit

import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults

/**
 * Stable per-install identifier used to scope refresh-token rotation — not a secret, so plain
 * NSUserDefaults is fine, same tradeoff apps/webApp makes with localStorage and androidApp makes
 * with plain SharedPreferences. Must be persisted, not regenerated per-process, or every relaunch
 * would fail the server's refresh-token device-match check ("DEVICE_MISMATCH") and force a fresh
 * login every time.
 */
fun getOrCreateDeviceId(): String {
    val defaults = NSUserDefaults.standardUserDefaults
    defaults.stringForKey(KEY_DEVICE_ID)?.let { return it }
    val created = NSUUID().UUIDString()
    defaults.setObject(created, forKey = KEY_DEVICE_ID)
    return created
}

private const val KEY_DEVICE_ID = "streaming_device_id"
