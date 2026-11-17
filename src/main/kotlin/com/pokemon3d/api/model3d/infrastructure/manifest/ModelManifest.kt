package com.pokemon3d.api.model3d.infrastructure.manifest

import com.pokemon3d.api.model3d.domain.ModelMetadata

data class ModelManifest(
    val schemaVersion: Int,
    val datasetVersion: String,
    val modelCount: Int,
    val models: List<ModelMetadata>,
)
