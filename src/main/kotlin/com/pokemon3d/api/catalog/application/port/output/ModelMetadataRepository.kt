package com.pokemon3d.api.catalog.application.port.output

import com.pokemon3d.api.catalog.domain.ModelMetadata
import com.pokemon3d.api.catalog.domain.VersionedModel
/** Read-only access to model metadata, independent of its storage format. */
interface ModelMetadataRepository {
    val datasetVersion: String
    fun findVersionedById(modelId: String): VersionedModel? = findById(modelId)?.let { VersionedModel(datasetVersion, it) }
    fun findAll(): List<ModelMetadata>
    fun findById(modelId: String): ModelMetadata?
    fun findByPokedexNumber(pokedexNumber: Int): List<ModelMetadata>
}
