package com.pokemon3d.api.model3d.infrastructure.persistence

import com.pokemon3d.api.model3d.domain.ModelMetadata
import jakarta.persistence.*
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.util.UUID

/** JSON stores the nested form, asset, motions and camera; lookup keys are indexed relational columns. */
@Entity
@Table(name = "model_metadata")
class ModelMetadataEntity(
    @Id val id: UUID,
    @Column(name = "release_id", nullable = false) val releaseId: UUID,
    @Column(name = "model_id", nullable = false, length = 200) val modelId: String,
    @Column(name = "pokedex_number", nullable = false) val pokedexNumber: Int,
    @Column(name = "metadata_json", nullable = false, columnDefinition = "text") val metadataJson: String,
)

internal object MetadataCodec {
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    fun encode(model: ModelMetadata): String = mapper.writeValueAsString(model)
    fun decode(json: String): ModelMetadata = mapper.readValue(json, ModelMetadata::class.java)
}
