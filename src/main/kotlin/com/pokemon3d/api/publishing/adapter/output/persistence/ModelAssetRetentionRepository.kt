package com.pokemon3d.api.publishing.adapter.output.persistence

import com.pokemon3d.api.assets.domain.ModelAssetRetention
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.publishing.application.port.output.ModelAssetRetentionStore
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class ModelAssetRetentionRepository(private val jdbc: JdbcTemplate) : ModelAssetRetentionStore {
    @Transactional
    override fun retainCurrentAssets(location: ModelStorageLocation): ModelAssetRetention {
        val active = jdbc.query("SELECT release_id, revision FROM model_active_release WHERE singleton = 1 FOR UPDATE") {
            rs, _ -> requireNotNull(rs.getObject(1, UUID::class.java)) { "No active release; refusing to prune" } to rs.getLong(2)
        }.single()
        check(jdbc.queryForObject<Boolean>("SELECT pruning FROM model_maintenance WHERE singleton = 1") != true) {
            "Another prune is running; after a crash, verify that process has stopped before clearing model_maintenance.pruning"
        }
        jdbc.update("UPDATE model_maintenance SET pruning = TRUE WHERE singleton = 1")
        val protectedAssets = jdbc.query("""
            SELECT DISTINCT a.id, a.object_path FROM model_asset a
            JOIN model_content c ON c.asset_id = a.id JOIN model_release_member m ON m.content_id = c.id
            JOIN model_release r ON r.id = m.release_id
            LEFT JOIN model_release legacy ON legacy.id = a.legacy_release_id
            WHERE COALESCE(a.storage_bucket, legacy.storage_bucket) = ?
              AND COALESCE(a.storage_prefix, legacy.storage_prefix) = ?
              AND (r.id = ? OR (r.base_revision >= ? AND r.state IN ('UPLOADING', 'READY')))
        """.trimIndent(), { rs, _ -> rs.getObject(1, UUID::class.java) to rs.getString(2) },
            location.bucket, location.prefix, active.first, active.second)
        val retainedIds = protectedAssets.map { it.first }.toSet()
        val candidates = jdbc.query("""SELECT a.id, a.object_path FROM model_asset a
            LEFT JOIN model_release legacy ON legacy.id = a.legacy_release_id
            WHERE COALESCE(a.storage_bucket, legacy.storage_bucket) = ?
              AND COALESCE(a.storage_prefix, legacy.storage_prefix) = ? """,
            { rs, _ -> rs.getObject(1, UUID::class.java) to rs.getString(2) }, location.bucket, location.prefix)
        // Disable reuse before deleting remote bytes. Concurrent registrations share the active-release lock.
        val obsolete = candidates.filterNot { it.first in retainedIds }
        obsolete.forEach {
            jdbc.update("UPDATE model_asset SET available = FALSE WHERE id = ?", it.first)
        }
        val paths = protectedAssets.map { it.second }.toMutableSet()
        // Old processes may still read the legacy snapshot during the rolling upgrade.
        jdbc.query("SELECT metadata_json FROM model_metadata WHERE release_id = ?",
            { rs, _ -> MetadataCodec.decode(rs.getString(1)).asset.path }, active.first).forEach { paths.add(it) }
        jdbc.query("SELECT metadata_json FROM model_upload_job WHERE state IN ('PENDING', 'RUNNING')"
        ) { rs, _ -> MetadataCodec.decode(rs.getString(1)).asset.path }.forEach { paths.add(it) }
        require(paths.isNotEmpty()) { "No protected assets; refusing to prune" }
        val retiredReleases = jdbc.query("""SELECT id FROM model_release
            WHERE id <> ? AND base_revision < ? AND storage_bucket = ? AND storage_prefix = ?""",
            { rs, _ -> rs.getObject(1, UUID::class.java).toString() }, active.first, active.second,
            location.bucket, location.prefix).toSet()
        return ModelAssetRetention(paths, obsolete.map { it.second }.toSet(), retiredReleases)
    }
    @Transactional
    override fun finishPruning() {
        jdbc.query("SELECT revision FROM model_active_release WHERE singleton = 1 FOR UPDATE") { rs, _ -> rs.getLong(1) }
        jdbc.update("UPDATE model_maintenance SET pruning = FALSE WHERE singleton = 1")
    }

}
