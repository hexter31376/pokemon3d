package com.pokemon3d.api.catalog.application

import com.pokemon3d.api.assets.domain.ModelAssetStorage
import com.pokemon3d.api.assets.domain.exception.ModelStorageException
import com.pokemon3d.api.assets.domain.exception.StorageFailure
import com.pokemon3d.api.catalog.domain.ModelMetadata
import com.pokemon3d.api.catalog.domain.ModelMetadataRepository
import java.net.URI

/** Application use case; orchestration only, independent of Spring and storage SDKs. */
class ModelResourceService(
    private val metadata: ModelMetadataRepository,
    private val storage: ModelAssetStorage?,
) {
    fun findById(modelId: String): ModelResource {
        val versionedModel = metadata.findVersionedById(modelId) ?: throw ModelNotFoundException(modelId)
        val model = versionedModel.model
        val assetStorage = storage ?: throw ModelStorageException(StorageFailure.UNAVAILABLE, model.asset.path)
        val storedAsset = assetStorage.find(model.asset, versionedModel.storageLocation)
            ?: throw ModelAssetNotFoundException(modelId)
        if (!storedAsset.matches(model.asset)) {
            throw ModelStorageException(StorageFailure.INTEGRITY_MISMATCH, model.asset.path)
        }
        return ModelResource(
            versionedModel.datasetVersion,
            model,
            assetStorage.publicUrl(model.asset, versionedModel.storageLocation),
            versionedModel.revision,
        )
    }
}

data class ModelResource(
    val datasetVersion: String,
    val model: ModelMetadata,
    val fileUrl: URI,
    val revision: Long,
)

sealed class ModelResourceNotFoundException(message: String) : RuntimeException(message)

class ModelNotFoundException(val modelId: String) : ModelResourceNotFoundException("Model not found: $modelId")

class ModelAssetNotFoundException(val modelId: String) : ModelResourceNotFoundException("Asset not found: $modelId")

class PokemonNotFoundException(val pokedexNumber: Int) :
    ModelResourceNotFoundException("Pokemon not found: $pokedexNumber")
