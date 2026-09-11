package com.streaming.platform.common

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorResponse(
    val code: String,
    val message: String,
    val correlationId: String,
    val details: Map<String, String> = emptyMap(),
)

@Serializable
data class Page<T>(
    val items: List<T>,
    val nextCursor: String? = null,
    val hasMore: Boolean = nextCursor != null,
)

@Serializable
data class CursorRequest(
    val cursor: String? = null,
    val limit: Int = 20,
)
