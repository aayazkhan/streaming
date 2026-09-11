package com.streaming.platform.network

/** Builds a query-param map for [ApiClient.get], dropping null/blank values. */
fun queryOf(vararg pairs: Pair<String, Any?>): Map<String, String> = buildMap {
    for ((key, value) in pairs) {
        if (value == null) continue
        val stringValue = value.toString()
        if (stringValue.isNotEmpty()) put(key, stringValue)
    }
}
