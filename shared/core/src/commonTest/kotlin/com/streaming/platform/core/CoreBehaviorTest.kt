package com.streaming.platform.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreBehaviorTest {
    @Test
    fun staticFeatureFlagsUseConfiguredAndDefaultValues() = runBlocking {
        val flags = StaticFeatureFlagReader(mapOf("new-home" to true))

        assertTrue(flags.isEnabled("new-home"))
        assertFalse(flags.isEnabled("missing"))
    }

    @Test
    fun retryPolicyRetriesUntilSuccess() = runBlocking {
        var attempts = 0
        val result = withRetry(RetryPolicy(maxAttempts = 3, initialDelayMillis = 0)) {
            attempts += 1
            if (attempts < 3) error("transient")
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(3, attempts)
    }
}
