package com.pokemon3d.api.model3d.infrastructure.storage.s3

import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.jdbc.core.JdbcTemplate

/** Pins existing releases to the storage coordinates in use during their first upgraded startup. */
class LegacyStorageLocationBackfill(
    private val jdbc: JdbcTemplate,
    private val properties: S3StorageProperties,
) : SmartInitializingSingleton {
    override fun afterSingletonsInstantiated() {
        val missing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM model_release WHERE storage_bucket IS NULL OR storage_prefix IS NULL",
            Int::class.java,
        ) ?: 0
        if (missing == 0) return

        require(properties.bucket.isNotBlank()) { "Cannot pin legacy releases without a configured storage bucket" }
        jdbc.update(
            """UPDATE model_release SET storage_bucket=COALESCE(storage_bucket,?),
                storage_prefix=COALESCE(storage_prefix,?) WHERE storage_bucket IS NULL OR storage_prefix IS NULL""",
            properties.bucket,
            properties.prefix,
        )
    }
}
