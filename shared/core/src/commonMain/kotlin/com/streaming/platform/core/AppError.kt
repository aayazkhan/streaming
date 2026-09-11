package com.streaming.platform.core

sealed interface AppError {
    val message: String

    data class Network(override val message: String, val cause: Throwable? = null) : AppError
    data class Unauthorized(override val message: String = "Authentication is required") : AppError
    data class Forbidden(override val message: String = "You do not have access to this resource") : AppError
    data class NotFound(override val message: String = "The requested resource was not found") : AppError
    data class Conflict(override val message: String) : AppError
    data class Validation(override val message: String, val fields: Map<String, String> = emptyMap()) : AppError
    data class RateLimited(override val message: String = "Too many requests") : AppError
    data class Server(override val message: String, val statusCode: Int? = null) : AppError
    data class Unknown(override val message: String, val cause: Throwable? = null) : AppError
}

typealias AppResult<T> = Result<T>

fun Throwable.toAppError(): AppError = AppError.Unknown(message ?: "Unexpected error", this)
