package com.streaming.platform.contentservice

import io.ktor.http.HttpStatusCode

class ContentException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : RuntimeException(message)
