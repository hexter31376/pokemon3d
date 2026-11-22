package com.pokemon3d.api.publishing.infrastructure.manifest

import com.pokemon3d.api.catalog.domain.ModelMetadata

data class ModelManifest(
    val schemaVersion: Int,
    val datasetVersion: String,
    val modelCount: Int,
    val models: List<ModelMetadata>,
)
