package com.pokemon3d.api.model3d.infrastructure.storage.s3

import com.pokemon3d.api.model3d.infrastructure.persistence.ModelReleaseEntity
import org.springframework.beans.factory.SmartInitializingSingleton
import jakarta.persistence.EntityManager
import org.springframework.transaction.annotation.Transactional

/** Pins existing releases to the storage coordinates in use during their first upgraded startup. */
open class LegacyStorageLocationBackfill(
    private val entityManager: EntityManager,
    private val properties: S3StorageProperties,
) : SmartInitializingSingleton {
    @Transactional
    override fun afterSingletonsInstantiated() {
        val releasesMissingStorageLocation = entityManager
            .createQuery(
                """
                select release from ModelReleaseEntity release
                where release.storageBucket is null or release.storagePrefix is null
                """.trimIndent(),
                ModelReleaseEntity::class.java,
            )
            .resultList
        if (releasesMissingStorageLocation.isEmpty()) return

        require(properties.bucket.isNotBlank()) { "Cannot pin legacy releases without a configured storage bucket" }
        releasesMissingStorageLocation.forEach { release ->
            if (release.storageBucket == null) release.storageBucket = properties.bucket
            if (release.storagePrefix == null) release.storagePrefix = properties.prefix
        }
    }
}
