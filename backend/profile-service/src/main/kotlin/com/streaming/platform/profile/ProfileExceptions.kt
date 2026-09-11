package com.streaming.platform.profile

import io.ktor.http.HttpStatusCode

class ProfileException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : RuntimeException(message)
