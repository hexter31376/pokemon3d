package com.pokemon3d.api.model3d.infrastructure.manifest

import com.pokemon3d.api.model3d.domain.ModelMetadataRepository
import com.pokemon3d.api.model3d.domain.ModelMetadata
import com.pokemon3d.api.model3d.domain.ModelBounds

import java.nio.file.Files
import java.nio.file.Path
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule

/** Loads once. No GLB reads, network access, updates, or partial fallback snapshots. */
class JsonManifestModelMetadataRepository(path: Path) : ModelMetadataRepository {
    private val snapshot: ModelManifest
    private val byId: Map<String, ModelMetadata>
    private val byNumber: Map<Int, List<ModelMetadata>>
    override val datasetVersion: String get() = snapshot.datasetVersion

    init {
        try {
            val mapper = JsonMapper.builder()
                .addModule(KotlinModule.Builder().enable(KotlinFeature.StrictNullChecks).build())
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .build()
            val manifest = Files.newInputStream(path).use { mapper.readValue(it, ModelManifest::class.java) }
            ManifestValidator.validate(manifest)
            snapshot = manifest.copy(models = immutable(manifest.models.map { model ->
                model.copy(
                    asset = model.asset.copy(requiredExtensions = immutable(model.asset.requiredExtensions)),
                    motions = immutable(model.motions),
                    camera = model.camera.copy(
                        direction = immutable(model.camera.direction), target = immutable(model.camera.target),
                        bounds = ModelBounds(immutable(model.camera.bounds.min), immutable(model.camera.bounds.max)),
                    ),
                )
            }))
            byId = snapshot.models.associateBy { it.modelId }
            byNumber = snapshot.models.groupBy { it.pokedexNumber }.mapValues { immutable(it.value) }
        } catch (ex: Exception) {
            throw ManifestLoadException("Cannot load model manifest '$path': ${ex.message}", ex)
        }
    }

    override fun findAll(): List<ModelMetadata> = snapshot.models
    override fun findById(modelId: String): ModelMetadata? = byId[modelId]
    override fun findByPokedexNumber(pokedexNumber: Int): List<ModelMetadata> = byNumber[pokedexNumber] ?: emptyList()

    private fun <T : Any> immutable(items: List<T>): List<T> = java.util.List.copyOf(items)
}
