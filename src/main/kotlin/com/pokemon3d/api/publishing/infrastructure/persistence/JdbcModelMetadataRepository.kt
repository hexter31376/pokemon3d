package com.pokemon3d.api.publishing.infrastructure.persistence

import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.catalog.domain.ModelMetadata
import com.pokemon3d.api.catalog.domain.ModelMetadataRepository
import com.pokemon3d.api.catalog.domain.VersionedModel
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(readOnly = true)
class JdbcModelMetadataRepository(private val jdbc: JdbcTemplate) : ModelMetadataRepository {
    override val datasetVersion: String
        get() = jdbc.query(
            "SELECT r.dataset_version FROM model_active_release active JOIN model_release r ON r.id = active.release_id WHERE active.singleton = 1",
        ) { rs, _ -> rs.getString(1) }.singleOrNull().orEmpty()

    override fun findVersionedById(modelId: String): VersionedModel? = findModels("AND c.model_id = ?", modelId).singleOrNull()
    override fun findById(modelId: String): ModelMetadata? = findVersionedById(modelId)?.model
    override fun findAll(): List<ModelMetadata> = findModels().map { it.model }
    override fun findByPokedexNumber(pokedexNumber: Int): List<ModelMetadata> =
        findModels("AND c.pokedex_number = ?", pokedexNumber).map { it.model }

    private fun findModels(filter: String = "", vararg values: Any): List<VersionedModel> = jdbc.query(
        """
        SELECT r.dataset_version, active.revision, c.metadata_json, a.object_path,
               COALESCE(a.storage_bucket, legacy.storage_bucket) AS storage_bucket,
               COALESCE(a.storage_prefix, legacy.storage_prefix) AS storage_prefix
        FROM model_active_release active JOIN model_release r ON r.id = active.release_id
        JOIN model_release_member member ON member.release_id = r.id
        JOIN model_content c ON c.id = member.content_id JOIN model_asset a ON a.id = c.asset_id
        LEFT JOIN model_release legacy ON legacy.id = a.legacy_release_id
        WHERE active.singleton = 1 $filter ORDER BY c.model_id
        """.trimIndent(),
        { rs, _ ->
            val model = MetadataCodec.decode(rs.getString("metadata_json"))
            VersionedModel(
                rs.getString("dataset_version"),
                model.copy(asset = model.asset.copy(path = rs.getString("object_path"))),
                ModelStorageLocation(
                    requireNotNull(rs.getString("storage_bucket")) { "Published asset has no bucket" },
                    requireNotNull(rs.getString("storage_prefix")) { "Published asset has no prefix" },
                ),
                rs.getLong("revision"),
            )
        }, *values,
    )
}
