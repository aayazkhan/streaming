package com.streaming.platform.network

import com.streaming.platform.common.ApiErrorResponse
import com.streaming.platform.core.AppError
import com.streaming.platform.core.AppResult
import com.streaming.platform.core.toAppError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

@Serializable
data class ApiClientConfig(
    val baseUrl: String,
    val requestTimeoutMillis: Long = 15_000,
)

fun interface AccessTokenProvider {
    suspend fun accessToken(): String?

    /** Attempts one token refresh; returns whether a retry is worth attempting. No-op by default. */
    suspend fun refreshToken(): Boolean = false
}

class ApiClient(
    @PublishedApi internal val httpClient: HttpClient,
    @PublishedApi internal val config: ApiClientConfig,
    @PublishedApi internal val accessTokenProvider: AccessTokenProvider,
) {
    suspend inline fun <reified T> get(path: String, query: Map<String, String> = emptyMap()): AppResult<T> =
        execute(HttpMethod.Get, path, query, null)

    suspend inline fun <reified T, reified B> post(path: String, body: B): AppResult<T> =
        execute(HttpMethod.Post, path, emptyMap(), body)

    suspend inline fun <reified T, reified B> put(path: String, body: B): AppResult<T> =
        execute(HttpMethod.Put, path, emptyMap(), body)

    suspend inline fun <reified T, reified B> patch(path: String, body: B): AppResult<T> =
        execute(HttpMethod.Patch, path, emptyMap(), body)

    suspend inline fun <reified T> delete(path: String): AppResult<T> =
        execute(HttpMethod.Delete, path, emptyMap(), null)

    @PublishedApi
    internal suspend fun performRequest(method: HttpMethod, requestPath: String, query: Map<String, String>, body: Any?) =
        httpClient.request {
            this.method = method
            url {
                val base = config.baseUrl.trimEnd('/')
                val scheme = base.substringBefore("://")
                val authorityAndPath = base.substringAfter("://")
                val authority = authorityAndPath.substringBefore('/')
                val basePath = authorityAndPath.substringAfter('/', "")
                protocol = if (scheme == "https") URLProtocol.HTTPS else URLProtocol.HTTP
                host = authority.substringBefore(':')
                port = authority.substringAfter(':', "").toIntOrNull() ?: protocol.defaultPort
                encodedPathSegments = listOf(basePath, requestPath.trimStart('/'))
                    .filter(String::isNotEmpty)
                    .flatMap { it.split('/') }
                    .filter(String::isNotEmpty)

                query.forEach { (key, value) -> parameters.append(key, value) }
            }
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            accessTokenProvider.accessToken()?.let { bearerAuth(it) }
            if (body != null) setBody(body)
        }

    @PublishedApi
    internal suspend inline fun <reified T> execute(
        method: HttpMethod,
        requestPath: String,
        query: Map<String, String>,
        body: Any?,
    ): AppResult<T> = runCatching {
        var response = performRequest(method, requestPath, query, body)
        // A short-lived access token expiring mid-session is routine, not an error — refresh once
        // and replay the request before surfacing anything to the caller.
        if (response.status == HttpStatusCode.Unauthorized && accessTokenProvider.refreshToken()) {
            response = performRequest(method, requestPath, query, body)
        }
        if (!response.status.isSuccess()) {
            val error = runCatching { response.body<ApiErrorResponse>() }.getOrNull()
            throw ApiClientException(response.status, error?.message ?: "Request failed")
        }
        response.body<T>()
    }.recoverCatching { error -> throw error.toAppErrorException() }
}

class ApiClientException(val status: HttpStatusCode, override val message: String) : RuntimeException(message)

@PublishedApi
internal fun Throwable.toAppErrorException(): Throwable = when (this) {
    is ApiClientException -> when (status) {
        HttpStatusCode.Unauthorized -> AppError.Unauthorized(message).asException()
        HttpStatusCode.Forbidden -> AppError.Forbidden(message).asException()
        HttpStatusCode.NotFound -> AppError.NotFound(message).asException()
        HttpStatusCode.Conflict -> AppError.Conflict(message).asException()
        HttpStatusCode.TooManyRequests -> AppError.RateLimited(message).asException()
        else -> AppError.Server(message, status.value).asException()
    }
    else -> toAppError().asException()
}

@PublishedApi
internal fun AppError.asException(): AppErrorException = AppErrorException(this)

class AppErrorException(val error: AppError) : RuntimeException(error.message)
