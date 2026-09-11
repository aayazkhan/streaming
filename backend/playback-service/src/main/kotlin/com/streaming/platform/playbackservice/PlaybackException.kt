package com.streaming.platform.playbackservice

import io.ktor.http.HttpStatusCode

class PlaybackException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : RuntimeException(message)
