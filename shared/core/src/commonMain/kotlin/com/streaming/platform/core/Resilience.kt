package com.streaming.platform.core

import kotlinx.coroutines.delay
import kotlin.math.min

data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMillis: Long = 250,
    val maxDelayMillis: Long = 5_000,
    val retryable: (Throwable) -> Boolean = { true },
)

suspend fun <T> withRetry(policy: RetryPolicy, block: suspend () -> T): T {
    require(policy.maxAttempts > 0) { "maxAttempts must be positive" }
    var attempt = 1
    var delayMillis = policy.initialDelayMillis
    while (true) {
        try {
            return block()
        } catch (error: Throwable) {
            if (attempt >= policy.maxAttempts || !policy.retryable(error)) throw error
            delay(delayMillis)
            delayMillis = min(policy.maxDelayMillis, delayMillis * 2)
            attempt += 1
        }
    }
}
