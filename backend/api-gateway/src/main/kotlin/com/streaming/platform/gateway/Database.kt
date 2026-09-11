package com.streaming.platform.gateway

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import javax.sql.DataSource

fun createDataSource(config: GatewayConfig): HikariDataSource = HikariDataSource(HikariConfig().apply {
    jdbcUrl = config.databaseUrl
    username = config.databaseUser
    password = config.databasePassword
    maximumPoolSize = 20
    minimumIdle = 2
    connectionTimeout = 5_000
    validationTimeout = 2_000
    leakDetectionThreshold = 10_000
    poolName = "streaming-platform-db"
})

class SchemaMigrator(private val dataSource: DataSource) {
    fun migrate() {
        val script = requireNotNull(javaClass.classLoader.getResource("db/migration/V1__foundation.sql")) {
            "Database migration resource is missing"
        }.readText()
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.createStatement().use { statement ->
                    script.split(';')
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .forEach(statement::addBatch)
                    statement.executeBatch()
                }
                connection.commit()
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }
}

fun DataSource.isReady(): Boolean = connection.use { it.isValid(2) }
