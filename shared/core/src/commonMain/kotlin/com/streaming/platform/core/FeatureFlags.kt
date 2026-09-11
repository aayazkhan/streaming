package com.streaming.platform.core

interface FeatureFlagReader {
    suspend fun isEnabled(key: String, subjectId: String? = null): Boolean
}

class StaticFeatureFlagReader(
    private val values: Map<String, Boolean>,
    private val defaultValue: Boolean = false,
) : FeatureFlagReader {
    override suspend fun isEnabled(key: String, subjectId: String?): Boolean = values[key] ?: defaultValue
}
