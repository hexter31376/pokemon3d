package com.pokemon3d.api.catalog.application.model

import com.pokemon3d.api.catalog.domain.ModelMetadata
import java.net.URI

data class ModelResource(
    val datasetVersion: String,
    val model: ModelMetadata,
    val fileUrl: URI,
    val revision: Long,
)
