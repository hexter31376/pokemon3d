package com.pokemon3d.api.model3d.application

import com.pokemon3d.api.model3d.domain.*
import com.pokemon3d.api.model3d.domain.exception.ModelStorageException
import com.pokemon3d.api.model3d.domain.exception.StorageFailure
import java.net.URI

/** Application use case; orchestration only, independent of Spring and storage SDKs. */
class ModelResourceService(
    private val metadata: ModelMetadataRepository,
    private val storage: ModelAssetStorage,
) {
    fun findById(modelId: String): ModelResource {
        val versionedModel = metadata.findVersionedById(modelId) ?: throw ModelNotFoundException(modelId)
        val model = versionedModel.model
        val storedAsset = storage.find(model.asset, versionedModel.storageLocation)
            ?: throw ModelAssetNotFoundException(modelId)
        if (!storedAsset.matches(model.asset)) {
            throw ModelStorageException(StorageFailure.INTEGRITY_MISMATCH, model.asset.path)
        }
        return ModelResource(
            versionedModel.datasetVersion,
            model,
            storage.publicUrl(model.asset, versionedModel.storageLocation),
        )
    }
}

data class ModelResource(val datasetVersion: String, val model: ModelMetadata, val fileUrl: URI)

sealed class ModelResourceNotFoundException(message: String) : RuntimeException(message)

class ModelNotFoundException(val modelId: String) : ModelResourceNotFoundException("Model not found: $modelId")

class ModelAssetNotFoundException(val modelId: String) : ModelResourceNotFoundException("Asset not found: $modelId")
