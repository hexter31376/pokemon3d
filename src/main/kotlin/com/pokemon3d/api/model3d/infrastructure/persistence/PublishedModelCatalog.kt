package com.pokemon3d.api.model3d.infrastructure.persistence

import com.pokemon3d.api.model3d.domain.ModelAsset
import com.pokemon3d.api.model3d.domain.ModelMetadata
import com.pokemon3d.api.model3d.domain.ModelStorageLocation
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

/** Mutations run under the active-release lock held by the upload repository. */
@Repository
class PublishedModelCatalog(private val jdbc: JdbcTemplate) {
    fun findAsset(asset: ModelAsset, location: ModelStorageLocation): PublishedAsset? = jdbc.query(
        """
        SELECT a.* FROM model_asset a LEFT JOIN model_release legacy ON legacy.id = a.legacy_release_id
        WHERE COALESCE(a.storage_bucket, legacy.storage_bucket) = ?
          AND COALESCE(a.storage_prefix, legacy.storage_prefix) = ? AND a.sha256 = ? AND a.available = TRUE
        ORDER BY a.id LIMIT 1
        """.trimIndent(),
        { rs, _ -> PublishedAsset(rs.getObject("id", UUID::class.java), rs.getString("object_path"), rs.getLong("size_bytes")) },
        location.bucket, location.prefix, asset.sha256,
    ).singleOrNull()?.also {
        require(it.sizeBytes == asset.sizeBytes) { "Conflicting size for published SHA-256 ${asset.sha256}" }
    }

    fun publish(model: ModelMetadata, location: ModelStorageLocation): UUID {
        val asset = findAsset(model.asset, location) ?: insertAsset(model.asset, location)
        val normalized = model.copy(asset = model.asset.copy(path = ""))
        val json = MetadataCodec.encode(normalized)
        val hash = MetadataCodec.fingerprint(json)
        val existing = jdbc.query(
            "SELECT id FROM model_content WHERE model_id = ? AND asset_id = ? AND metadata_hash = ?",
            { rs, _ -> rs.getObject(1, UUID::class.java) }, model.modelId, asset.id, hash,
        ).singleOrNull()
        if (existing != null) return existing
        val id = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO model_content(id, model_id, pokedex_number, asset_id, metadata_hash, metadata_json) VALUES (?, ?, ?, ?, ?, ?)",
            id, model.modelId, model.pokedexNumber, asset.id, hash, json,
        )
        return id
    }

    private fun insertAsset(asset: ModelAsset, location: ModelStorageLocation): PublishedAsset {
        val id = UUID.randomUUID()
        jdbc.update(
            """INSERT INTO model_asset(id, storage_bucket, storage_prefix, sha256, size_bytes, object_path, available)
               VALUES (?, ?, ?, ?, ?, ?, TRUE)""",
            id, location.bucket, location.prefix, asset.sha256, asset.sizeBytes, asset.path,
        )
        return PublishedAsset(id, asset.path, asset.sizeBytes)
    }
}

data class PublishedAsset(val id: UUID, val path: String, val sizeBytes: Long)
