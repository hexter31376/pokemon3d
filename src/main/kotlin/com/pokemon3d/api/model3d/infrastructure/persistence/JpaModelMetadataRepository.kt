package com.pokemon3d.api.model3d.infrastructure.persistence

import com.pokemon3d.api.model3d.domain.ModelMetadata
import com.pokemon3d.api.model3d.domain.ModelMetadataRepository
import com.pokemon3d.api.model3d.domain.ModelStorageLocation
import com.pokemon3d.api.model3d.domain.VersionedModel
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(readOnly = true)
class JpaModelMetadataRepository(private val entityManager: EntityManager) : ModelMetadataRepository {
    private companion object {
        const val ACTIVE_RELEASE_ID = 1
    }

    override val datasetVersion: String
        get() = entityManager.createQuery(
            """select release.datasetVersion from ModelActiveReleaseEntity active
                join active.release release where active.singleton = :activeReleaseId""",
            String::class.java,
        )
            .setParameter("activeReleaseId", ACTIVE_RELEASE_ID)
            .resultList
            .singleOrNull()
            .orEmpty()

    override fun findVersionedById(modelId: String): VersionedModel? =
        entityManager.createQuery(
            """select new com.pokemon3d.api.model3d.infrastructure.persistence.VersionedMetadataProjection(
                    release.datasetVersion, metadata.metadataJson, release.storageBucket, release.storagePrefix)
                from ModelActiveReleaseEntity active
                join active.release release
                join release.metadata metadata
                where active.singleton = :activeReleaseId and metadata.modelId = :modelId""",
            VersionedMetadataProjection::class.java,
        )
            .setParameter("activeReleaseId", ACTIVE_RELEASE_ID)
            .setParameter("modelId", modelId)
            .resultList
            .singleOrNull()
            ?.toVersionedModel()

    override fun findById(modelId: String): ModelMetadata? = findVersionedById(modelId)?.model

    override fun findAll(): List<ModelMetadata> = findMetadata()

    override fun findByPokedexNumber(pokedexNumber: Int): List<ModelMetadata> =
        findMetadata(pokedexNumber)

    private fun findMetadata(pokedexNumber: Int? = null): List<ModelMetadata> = entityManager.createQuery(
        """select metadata from ModelActiveReleaseEntity active
            join active.release release
            join release.metadata metadata
            where active.singleton = :activeReleaseId
                and (:pokedexNumber is null or metadata.pokedexNumber = :pokedexNumber)
            order by metadata.modelId""",
        ModelMetadataEntity::class.java,
    )
        .setParameter("activeReleaseId", ACTIVE_RELEASE_ID)
        .setParameter("pokedexNumber", pokedexNumber)
        .resultList
        .map { MetadataCodec.decode(it.metadataJson) }

    private fun VersionedMetadataProjection.toVersionedModel() = VersionedModel(
        datasetVersion,
        MetadataCodec.decode(metadataJson),
        ModelStorageLocation(
            requireNotNull(storageBucket) { "Active model release has no storage bucket" },
            requireNotNull(storagePrefix) { "Active model release has no storage prefix" },
        ),
    )
}

internal data class VersionedMetadataProjection(
    val datasetVersion: String,
    val metadataJson: String,
    val storageBucket: String?,
    val storagePrefix: String?,
)
