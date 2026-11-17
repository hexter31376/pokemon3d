package com.pokemon3d.api.model3d.domain

/** Read-only access to model metadata, independent of its storage format. */
interface ModelMetadataRepository {
    val datasetVersion: String
    fun findAll(): List<ModelMetadata>
    fun findById(modelId: String): ModelMetadata?
    fun findByPokedexNumber(pokedexNumber: Int): List<ModelMetadata>
}

