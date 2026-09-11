package com.streaming.platform.ioskit

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import platform.Foundation.NSLog

/**
 * Swift can't easily drive Ktor's HttpClientConfig builder DSL, so this factory does it in Kotlin —
 * mirrors androidApp's AppContainer.httpClient (Darwin engine instead of OkHttp).
 */
fun makeHttpClient(debugLogging: Boolean): HttpClient = HttpClient(Darwin) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false })
    }
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) {
                NSLog("KtorClient: %s", message)
            }
        }
        level = if (debugLogging) LogLevel.INFO else LogLevel.NONE
    }
}
