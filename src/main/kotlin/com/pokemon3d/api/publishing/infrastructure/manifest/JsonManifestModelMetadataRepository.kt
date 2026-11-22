package com.pokemon3d.api.publishing.infrastructure.manifest

import com.pokemon3d.api.catalog.domain.ModelBounds
import com.pokemon3d.api.catalog.domain.ModelMetadata
import com.pokemon3d.api.catalog.domain.ModelMetadataRepository
import java.nio.file.Path
/** Offline validation helper only; runtime metadata queries use the DB repository. */
class JsonManifestModelMetadataRepository(path: Path) : ModelMetadataRepository {
    private val snapshot: ModelManifest
    private val byId: Map<String, ModelMetadata>
    private val byNumber: Map<Int, List<ModelMetadata>>
    override val datasetVersion: String get() = snapshot.datasetVersion

    init {
        try {
            val manifest = ManifestJson.read(path)
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
            byNumber = snapshot.models
                .groupBy { it.pokedexNumber }
                .mapValues { immutable(it.value) }
        } catch (ex: Exception) {
            throw ManifestLoadException("Cannot load model manifest '$path': ${ex.message}", ex)
        }
    }

    override fun findAll(): List<ModelMetadata> = snapshot.models
    override fun findById(modelId: String): ModelMetadata? = byId[modelId]
    override fun findByPokedexNumber(pokedexNumber: Int): List<ModelMetadata> = byNumber[pokedexNumber] ?: emptyList()

    private fun <T : Any> immutable(items: List<T>): List<T> = java.util.List.copyOf(items)
}
