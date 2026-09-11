package com.streaming.platform.watchlistservice

import io.ktor.http.HttpStatusCode

class WatchlistException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : RuntimeException(message)
