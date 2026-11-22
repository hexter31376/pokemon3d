package com.pokemon3d.api.catalog.domain

import com.pokemon3d.api.assets.domain.ModelStorageLocation

data class VersionedModel(
    val datasetVersion: String,
    val model: ModelMetadata,
    val storageLocation: ModelStorageLocation = ModelStorageLocation("", ""),
    val revision: Long = 0,
)
