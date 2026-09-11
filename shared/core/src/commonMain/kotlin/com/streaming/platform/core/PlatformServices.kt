package com.streaming.platform.core

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

fun interface TimeProvider {
    fun now(): Instant
}

object SystemTimeProvider : TimeProvider {
    override fun now(): Instant = Clock.System.now()
}

fun interface IdProvider {
    fun newId(): String
}

object UuidProvider : IdProvider {
    override fun newId(): String = "${kotlin.random.Random.nextLong().toString(16)}-${kotlin.random.Random.nextLong().toString(16)}"
}

interface AppLogger {
    fun debug(message: String, attributes: Map<String, String> = emptyMap())
    fun info(message: String, attributes: Map<String, String> = emptyMap())
    fun warn(message: String, attributes: Map<String, String> = emptyMap())
    fun error(message: String, cause: Throwable? = null, attributes: Map<String, String> = emptyMap())
}

object NoopLogger : AppLogger {
    override fun debug(message: String, attributes: Map<String, String>) = Unit
    override fun info(message: String, attributes: Map<String, String>) = Unit
    override fun warn(message: String, attributes: Map<String, String>) = Unit
    override fun error(message: String, cause: Throwable?, attributes: Map<String, String>) = Unit
}
