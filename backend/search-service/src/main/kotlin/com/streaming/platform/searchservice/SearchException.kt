package com.streaming.platform.searchservice

import io.ktor.http.HttpStatusCode

class SearchException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : RuntimeException(message)
