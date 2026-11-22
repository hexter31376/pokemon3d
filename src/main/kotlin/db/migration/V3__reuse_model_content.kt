package db.migration

import com.pokemon3d.api.model3d.domain.ModelStorageLocation
import com.pokemon3d.api.model3d.infrastructure.persistence.MetadataCodec
import com.pokemon3d.api.model3d.infrastructure.persistence.PublishedModelCatalog
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import java.util.UUID

class V3__reuse_model_content : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val jdbc = JdbcTemplate(SingleConnectionDataSource(context.connection, true))
        if (context.connection.metaData.databaseProductName == "PostgreSQL") {
            // Existing workers lock the queue before releases; acquire DDL locks in that same order.
            jdbc.execute("LOCK TABLE model_upload_job IN ACCESS EXCLUSIVE MODE")
        }
        createTables(jdbc)
        migratePublishedModels(jdbc)
        // Keep the old snapshot table readable during a rolling upgrade; new code never writes it.
        jdbc.execute("ALTER TABLE model_upload_job ALTER COLUMN metadata_json DROP NOT NULL")
        jdbc.update("UPDATE model_upload_job SET metadata_json = NULL WHERE state = 'SUCCEEDED'")
    }

    private fun createTables(jdbc: JdbcTemplate) {
        jdbc.execute("CREATE TABLE model_maintenance (singleton INTEGER PRIMARY KEY CHECK (singleton = 1), pruning BOOLEAN NOT NULL)")
        jdbc.update("INSERT INTO model_maintenance VALUES (1, FALSE)")
        jdbc.execute("""CREATE TABLE model_asset (
            id UUID PRIMARY KEY, storage_bucket VARCHAR(63), storage_prefix VARCHAR(200),
            legacy_release_id UUID REFERENCES model_release(id), sha256 VARCHAR(64) NOT NULL,
            size_bytes BIGINT NOT NULL CHECK (size_bytes > 0), object_path TEXT NOT NULL,
            available BOOLEAN NOT NULL DEFAULT TRUE
        )""")
        jdbc.execute("CREATE INDEX model_asset_digest ON model_asset(storage_bucket, storage_prefix, sha256)")
        jdbc.execute("""CREATE TABLE model_content (
            id UUID PRIMARY KEY, model_id VARCHAR(200) NOT NULL, pokedex_number INTEGER NOT NULL,
            asset_id UUID NOT NULL REFERENCES model_asset(id), metadata_hash VARCHAR(64) NOT NULL,
            metadata_json TEXT NOT NULL, UNIQUE(model_id, asset_id, metadata_hash)
        )""")
        jdbc.execute("CREATE INDEX model_content_pokedex ON model_content(pokedex_number)")
        jdbc.execute("""CREATE TABLE model_release_member (
            release_id UUID NOT NULL REFERENCES model_release(id), model_id VARCHAR(200) NOT NULL,
            content_id UUID NOT NULL REFERENCES model_content(id), PRIMARY KEY(release_id, model_id)
        )""")
        jdbc.execute("CREATE INDEX model_release_member_content ON model_release_member(content_id)")
    }

    private fun migratePublishedModels(jdbc: JdbcTemplate) {
        val catalog = PublishedModelCatalog(jdbc)
        val legacyAssets = mutableMapOf<Pair<UUID, String>, UUID>()
        val rows = jdbc.query("""
            SELECT m.*, r.storage_bucket, r.storage_prefix FROM model_metadata m
            JOIN model_release r ON r.id = m.release_id
            ORDER BY CASE WHEN r.id = (SELECT release_id FROM model_active_release WHERE singleton = 1)
                          THEN 0 ELSE 1 END, r.created_at, m.id
        """.trimIndent()) { rs, _ ->
            LegacyModel(rs.getObject("release_id", UUID::class.java), rs.getString("metadata_json"),
                rs.getString("storage_bucket"), rs.getString("storage_prefix"))
        }
        rows.forEach { row ->
            val model = MetadataCodec.decode(row.json)
            val content = if (row.bucket != null && row.prefix != null) {
                catalog.publish(model, ModelStorageLocation(row.bucket, row.prefix))
            } else {
                val assetId = legacyAssets.getOrPut(row.releaseId to model.asset.sha256) {
                    val id = UUID.randomUUID()
                    jdbc.update("""INSERT INTO model_asset(id, legacy_release_id, sha256, size_bytes, object_path)
                        VALUES (?, ?, ?, ?, ?)""", id, row.releaseId, model.asset.sha256, model.asset.sizeBytes, model.asset.path)
                    id
                }
                val json = MetadataCodec.encode(model.copy(asset = model.asset.copy(path = "")))
                val id = UUID.randomUUID()
                jdbc.update("INSERT INTO model_content VALUES (?, ?, ?, ?, ?, ?)",
                    id, model.modelId, model.pokedexNumber, assetId, MetadataCodec.fingerprint(json), json)
                id
            }
            jdbc.update("INSERT INTO model_release_member VALUES (?, ?, ?)", row.releaseId, model.modelId, content)
        }
    }

    private data class LegacyModel(val releaseId: UUID, val json: String, val bucket: String?, val prefix: String?)
}
