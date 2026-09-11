package com.streaming.platform.gateway

import com.streaming.platform.identity.TokenIssuerConfig
import com.streaming.platform.storage.S3CompatibleStorageConfig
import java.time.Duration

data class GatewayConfig(
    val port: Int,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val jwt: TokenIssuerConfig,
    val corsOrigins: Set<String>,
    val cdnBaseUrl: String,
    val publicAssetBaseUrl: String?,
    val openSearchEndpoint: String?,
    val openSearchIndex: String,
    val openSearchApiKey: String?,
    val mediaStorage: S3CompatibleStorageConfig,
    val adminBootstrapSecret: String?,
) {
    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): GatewayConfig {
            fun required(name: String): String = environment[name]?.takeIf(String::isNotBlank)
                ?: error("Missing required environment variable: $name")

            return GatewayConfig(
                port = environment["PORT"]?.toIntOrNull() ?: 8080,
                databaseUrl = required("DATABASE_URL"),
                databaseUser = required("DATABASE_USER"),
                databasePassword = required("DATABASE_PASSWORD"),
                jwt = TokenIssuerConfig(
                    issuer = environment["JWT_ISSUER"] ?: "streaming-platform",
                    audience = environment["JWT_AUDIENCE"] ?: "streaming-client",
                    secret = required("JWT_SECRET").also {
                        require(it.length >= 32) { "JWT_SECRET must be at least 32 characters" }
                    },
                    accessTokenTtl = Duration.ofMinutes(environment["ACCESS_TOKEN_TTL_MINUTES"]?.toLongOrNull() ?: 10),
                    refreshTokenTtl = Duration.ofDays(environment["REFRESH_TOKEN_TTL_DAYS"]?.toLongOrNull() ?: 30),
                ),
                corsOrigins = (environment["CORS_ORIGINS"] ?: "http://localhost:3000")
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toSet(),
                cdnBaseUrl = required("CDN_BASE_URL"),
                publicAssetBaseUrl = environment["PUBLIC_ASSET_BASE_URL"]?.takeIf(String::isNotBlank),
                openSearchEndpoint = environment["OPENSEARCH_ENDPOINT"]?.takeIf(String::isNotBlank),
                openSearchIndex = environment["OPENSEARCH_INDEX"] ?: "content",
                openSearchApiKey = environment["OPENSEARCH_API_KEY"]?.takeIf(String::isNotBlank),
                mediaStorage = S3CompatibleStorageConfig(
                    endpoint = environment["MEDIA_STORAGE_ENDPOINT"] ?: "http://localhost:9000",
                    // Presigned upload/download URLs are used by browsers/clients outside the
                    // backend's own network — falls back to MEDIA_STORAGE_ENDPOINT when unset,
                    // which is only correct when that endpoint is already client-reachable.
                    publicEndpoint = environment["MEDIA_STORAGE_PUBLIC_ENDPOINT"]?.takeIf(String::isNotBlank)
                        ?: environment["MEDIA_STORAGE_ENDPOINT"] ?: "http://localhost:9000",
                    accessKey = environment["MEDIA_STORAGE_ACCESS_KEY"] ?: "minioadmin",
                    secretKey = environment["MEDIA_STORAGE_SECRET_KEY"] ?: "minioadmin",
                    bucket = environment["MEDIA_STORAGE_BUCKET"] ?: "media",
                    region = environment["MEDIA_STORAGE_REGION"] ?: "us-east-1",
                ),
                adminBootstrapSecret = environment["ADMIN_BOOTSTRAP_SECRET"]?.takeIf(String::isNotBlank),
            )
        }
    }
}
