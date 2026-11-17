package com.pokemon3d.api.model3d.application

import com.pokemon3d.api.model3d.domain.*
import com.pokemon3d.api.model3d.domain.ModelMetadata
import com.pokemon3d.api.model3d.domain.exception.ModelStorageException
import com.pokemon3d.api.model3d.domain.exception.StorageFailure
import java.net.URI

/** Application use case; orchestration only, independent of Spring and storage SDKs. */
class ModelResourceService(
    private val metadata: ModelMetadataRepository,
    private val storage: ModelAssetStorage,
) {
    fun findById(modelId: String): ModelResource {
        val model = metadata.findById(modelId) ?: throw ModelResourceNotFoundException(modelId, false)
        val stored = storage.find(model.asset) ?: throw ModelResourceNotFoundException(modelId, true)
        if (!stored.matches(model.asset)) {
            throw ModelStorageException(StorageFailure.INTEGRITY_MISMATCH, model.asset.path)
        }
        return ModelResource(metadata.datasetVersion, model, storage.publicUrl(model.asset))
    }
}

data class ModelResource(val datasetVersion: String, val model: ModelMetadata, val fileUrl: URI)
class ModelResourceNotFoundException(val modelId: String, val missingAsset: Boolean) :
    RuntimeException("${if (missingAsset) "Asset" else "Model"} not found: $modelId")
