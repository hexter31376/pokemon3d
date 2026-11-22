package com.pokemon3d.api.publishing.adapter.output.persistence

import com.pokemon3d.api.assets.domain.ModelStorageLocation
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.transaction.annotation.Transactional

/** Pins existing releases to the storage coordinates in use during their first upgraded startup. */
open class LegacyStorageLocationBackfill(
    private val entityManager: EntityManager,
    private val location: ModelStorageLocation,
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

        require(location.bucket.isNotBlank()) { "Cannot pin legacy releases without a configured storage bucket" }
        releasesMissingStorageLocation.forEach { release ->
            if (release.storageBucket == null) release.storageBucket = location.bucket
            if (release.storagePrefix == null) release.storagePrefix = location.prefix
        }
    }
}
