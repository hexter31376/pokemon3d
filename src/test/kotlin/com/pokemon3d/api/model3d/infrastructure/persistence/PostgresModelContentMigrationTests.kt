package com.pokemon3d.api.model3d.infrastructure.persistence

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.DriverManager
import java.util.UUID

@Tag("databaseIntegration")
class PostgresModelContentMigrationTests {
    @Test
    fun `existing PostgreSQL release snapshots migrate without changing active content`() {
        val url = requireNotNull(System.getenv("MODEL_DATABASE_URL"))
        val user = requireNotNull(System.getenv("MODEL_DATABASE_USERNAME"))
        val password = requireNotNull(System.getenv("MODEL_DATABASE_PASSWORD"))
        val schema = "migration_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.createStatement().use { it.execute("CREATE SCHEMA $schema") }
            try {
                val scopedUrl = url + (if ('?' in url) "&" else "?") + "currentSchema=$schema"
                verifyLegacyContentUpgrade(DriverManagerDataSource(scopedUrl, user, password))
            } finally {
                connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
            }
        }
    }
}
