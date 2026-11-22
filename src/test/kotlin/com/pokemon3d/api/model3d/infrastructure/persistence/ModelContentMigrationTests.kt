package com.pokemon3d.api.model3d.infrastructure.persistence

import com.pokemon3d.api.model3d.infrastructure.manifest.ManifestJson
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import javax.sql.DataSource
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals

class ModelContentMigrationTests {
    @Test
    fun `upgrade reuses legacy content and preserves the active snapshot path`() {
        val url = "jdbc:h2:mem:migration-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
        val dataSource = SingleConnectionDataSource(url, "sa", "", true)
        verifyLegacyContentUpgrade(dataSource)
    }
}

internal fun verifyLegacyContentUpgrade(dataSource: DataSource) {
        Flyway.configure().dataSource(dataSource).target("2").load().migrate()
        val jdbc = JdbcTemplate(dataSource)
        val fixture = Path.of(requireNotNull(ModelContentMigrationTests::class.java.getResource("/manifest/valid.json")).toURI())
        val model = ManifestJson.read(fixture).models.single()
        val active = UUID.randomUUID()
        val older = UUID.randomUUID()
        listOf(older, active).forEachIndexed { index, release ->
            jdbc.update("""INSERT INTO model_release(id, request_key, dataset_version, fingerprint,
                expected_count, base_revision, storage_bucket, storage_prefix, state)
                VALUES (?, ?, ?, ?, 1, ?, 'bucket', 'prefix', 'READY')""",
                release, "request-$index", "version-$index", "0".repeat(64), index)
            jdbc.update("INSERT INTO model_metadata VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), release,
                model.modelId, model.pokedexNumber,
                MetadataCodec.encode(model.copy(asset = model.asset.copy(path = "releases/$release/model.glb"))))
        }
        jdbc.update("UPDATE model_active_release SET release_id = ?, revision = 2 WHERE singleton = 1", active)

        Flyway.configure().dataSource(dataSource).load().migrate()

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM model_asset", Int::class.java))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM model_content", Int::class.java))
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM model_release_member", Int::class.java))
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM model_metadata", Int::class.java))
        val repository = JdbcModelMetadataRepository(jdbc)
        assertEquals("version-1", repository.datasetVersion)
        assertEquals("releases/$active/model.glb", repository.findById(model.modelId)?.asset?.path)
}
