package com.streaming.platform.identity

import io.ktor.http.HttpStatusCode

class IdentityException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
    val details: Map<String, String> = emptyMap(),
) : RuntimeException(message)
