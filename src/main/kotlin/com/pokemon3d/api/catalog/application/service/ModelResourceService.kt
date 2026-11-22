package com.pokemon3d.api.catalog.application.service

import com.pokemon3d.api.assets.application.port.input.FindModelAsset
import com.pokemon3d.api.catalog.application.exception.ModelAssetNotFoundException
import com.pokemon3d.api.catalog.application.exception.ModelNotFoundException
import com.pokemon3d.api.catalog.application.model.ModelResource
import com.pokemon3d.api.catalog.application.port.input.FindModelResource
import com.pokemon3d.api.catalog.application.port.output.ModelMetadataRepository

/** Application use case; orchestration only, independent of Spring and storage SDKs. */
class ModelResourceService(
    private val metadata: ModelMetadataRepository,
    private val assets: FindModelAsset,
) : FindModelResource {
    override fun findById(modelId: String): ModelResource {
        val versionedModel = metadata.findVersionedById(modelId) ?: throw ModelNotFoundException(modelId)
        val model = versionedModel.model
        val fileUrl = assets.findUrl(model.asset, versionedModel.storageLocation)
            ?: throw ModelAssetNotFoundException(modelId)
        return ModelResource(
            versionedModel.datasetVersion,
            model,
            fileUrl,
            versionedModel.revision,
        )
    }
}
