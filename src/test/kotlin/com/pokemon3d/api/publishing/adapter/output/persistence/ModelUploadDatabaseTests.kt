package com.pokemon3d.api.publishing.adapter.output.persistence

import com.pokemon3d.api.assets.adapter.output.s3.createDataset
import com.pokemon3d.api.assets.application.port.input.PublishModelAsset
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.catalog.application.port.output.ModelMetadataRepository
import com.pokemon3d.api.publishing.adapter.output.filesystem.CompletedUploadSpool
import com.pokemon3d.api.publishing.adapter.output.filesystem.FileModelUploadSpool
import com.pokemon3d.api.publishing.adapter.output.manifest.JsonImportManifestReader
import com.pokemon3d.api.publishing.adapter.output.manifest.tools.ManifestWriter
import com.pokemon3d.api.publishing.application.port.output.ModelUploadQueue
import com.pokemon3d.api.publishing.application.service.ModelImportService
import com.pokemon3d.api.publishing.application.service.ModelUploadWorker
import com.pokemon3d.api.publishing.domain.UploadJob
import com.pokemon3d.api.publishing.domain.UploadRejectedException
import jakarta.persistence.EntityManager
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:uploads;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "pokemon.uploads.enabled=false", "pokemon.storage.s3.enabled=false",
])
class ModelUploadDatabaseTests {
    @Autowired lateinit var jobs: JdbcModelPublishingRepository
    @Autowired lateinit var models: ModelMetadataRepository
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var entityManager: EntityManager
    @Autowired lateinit var retention: ModelAssetRetentionRepository
    @Autowired lateinit var transactionManager: PlatformTransactionManager
    @TempDir lateinit var root: Path
    private val lease = Duration.ofMinutes(10)
    private val storageLocation = ModelStorageLocation("test-bucket", "test-prefix")

    @BeforeEach fun clean() {
        jdbc.update("UPDATE model_active_release SET release_id=NULL,revision=0")
        jdbc.update("UPDATE model_maintenance SET pruning=FALSE WHERE singleton=1")
        jdbc.update("DELETE FROM model_release_member")
        jdbc.update("DELETE FROM model_content")
        jdbc.update("DELETE FROM model_asset")
        jdbc.update("DELETE FROM model_metadata")
        jdbc.update("DELETE FROM model_upload_job")
        jdbc.update("DELETE FROM model_release")
    }

    private fun submit(version: String = UUID.randomUUID().toString(), key: String = version, changedBinary: Boolean = false): UUID {
        val directory = Files.createDirectories(root.resolve(version))
        val (path, manifest) = createDataset(directory, if (changedBinary) version else "")
        ManifestWriter.writeAtomic(path, manifest.copy(datasetVersion = version))
        return ModelImportService(jobs, JsonImportManifestReader(), FileModelUploadSpool(root.resolve("spool")), storageLocation).submit(directory, path, key)
    }

    @Test fun `stages immutable files and publishes only after verified completion and activation`() {
        val id = submit("v1")
        assertTrue(models.findAll().isEmpty())
        assertFailsWith<UploadRejectedException> { jobs.activate(id) }
        val job = jobs.claim(lease, 3)!!
        assertEquals("objects/sha256/${job.model.asset.sha256}.glb", job.model.asset.path)
        Files.delete(root.resolve("v1/models/fixture_001.glb"))
        assertTrue(Files.exists(job.source))
        assertTrue(jobs.complete(job))
        assertEquals("READY", jobs.status(id).state)
        assertTrue(models.findAll().isEmpty())
        jobs.activate(id)
        assertEquals("v1", models.findVersionedById(job.model.modelId)!!.datasetVersion)
        assertEquals(job.model, models.findById(job.model.modelId))
        assertEquals(listOf(job.model), models.findByPokedexNumber(job.model.pokedexNumber))
        jobs.activate(id) // idempotent activation
    }

    @Test fun `unchanged snapshot reuses published metadata and creates no upload job`() {
        val first = submit("first")
        jobs.complete(jobs.claim(lease, 3)!!)
        jobs.activate(first)

        val second = submit("second")

        assertEquals("READY", jobs.status(second).state)
        assertEquals(1, jobs.status(second).succeeded)
        assertNull(jobs.claim(lease, 3))
        assertEquals(1, jdbc.queryForObject<Int>("SELECT COUNT(*) FROM model_upload_job"))
        jobs.activate(second)
        assertEquals("second", models.datasetVersion)
    }

    @Test fun `metadata-only patch preserves other models and needs neither source bytes nor upload`() {
        val (path, manifest) = createDataset(root)
        val first = manifest.models.single()
        val second = first.copy(modelId = "fixture_002", asset = first.asset.copy(path = "models/fixture_002.glb"))
        Files.copy(root.resolve(first.asset.path), root.resolve(second.asset.path))
        ManifestWriter.writeAtomic(path, manifest.copy(modelCount = 2, models = listOf(first, second)))
        val importer = ModelImportService(jobs, JsonImportManifestReader(), FileModelUploadSpool(root.resolve("spool")), storageLocation)
        val base = importer.submit(root, path, "base")
        while (true) {
            val job = jobs.claim(lease, 3) ?: break
            jobs.complete(job)
        }
        jobs.activate(base)
        val original = models.findById(second.modelId)
        val changed = first.copy(nameKo = "수정된 이름")
        ManifestWriter.writeAtomic(path, manifest.copy(datasetVersion = "patch", models = listOf(changed)))
        Files.delete(root.resolve(first.asset.path))

        val patch = importer.patch(root, path, "patch")

        assertEquals(2, jobs.status(patch).expected)
        assertEquals(2, jobs.status(patch).succeeded)
        assertEquals("READY", jobs.status(patch).state)
        assertNull(jobs.claim(lease, 3))
        assertFalse(Files.exists(root.resolve("spool").resolve(patch.toString())))
        assertEquals(1, jdbc.queryForObject<Int>("SELECT COUNT(*) FROM model_asset"))
        assertEquals(3, jdbc.queryForObject<Int>("SELECT COUNT(*) FROM model_content"))
        assertEquals(manifest.datasetVersion, models.datasetVersion)
        jobs.activate(patch)
        assertEquals("수정된 이름", models.findById(first.modelId)?.nameKo)
        assertEquals(original, models.findById(second.modelId))
        assertEquals(patch, importer.patch(root, path, "patch"))
    }

    @Test fun `patch requires an active base release`() {
        val (path, _) = createDataset(root)
        assertFailsWith<UploadRejectedException> {
            ModelImportService(jobs, JsonImportManifestReader(), FileModelUploadSpool(root.resolve("spool")), storageLocation).patch(root, path, "patch")
        }
    }

    @Test fun `same hash in a different storage location is not treated as published`() {
        val current = submit("current")
        jobs.complete(jobs.claim(lease, 3)!!)
        jobs.activate(current)
        val directory = Files.createDirectories(root.resolve("other"))
        val (path, manifest) = createDataset(directory)
        ManifestWriter.writeAtomic(path, manifest.copy(datasetVersion = "other"))
        val next = ModelImportService(jobs, JsonImportManifestReader(), FileModelUploadSpool(root.resolve("spool")), storageLocation.copy(prefix = "different"))
            .submit(directory, path, "other")
        assertEquals("UPLOADING", jobs.status(next).state)
        assertNotNull(jobs.claim(lease, 3))
    }

    @Test fun `binary patch stages only its changed model and preserves the rest`() {
        val first = submit("base")
        jobs.complete(jobs.claim(lease, 3)!!)
        jobs.activate(first)
        val before = models.findAll().single()
        val directory = Files.createDirectories(root.resolve("binary-patch"))
        val (path, manifest) = createDataset(directory, "changed bytes")
        ManifestWriter.writeAtomic(path, manifest.copy(datasetVersion = "binary-patch"))
        val patch = ModelImportService(jobs, JsonImportManifestReader(), FileModelUploadSpool(root.resolve("spool")), storageLocation).patch(directory, path, "binary")
        assertEquals(1, jobs.status(patch).expected)
        assertEquals("UPLOADING", jobs.status(patch).state)
        assertEquals(before, models.findAll().single())
        val job = jobs.claim(lease, 3)!!
        assertNotEquals(before.asset.sha256, job.model.asset.sha256)
        assertTrue(jobs.complete(job))
        assertNull(jobs.claim(lease, 3))
        jobs.activate(patch)
        assertEquals(job.model, models.findAll().single())
    }

    @Test fun `pruning retires only unreferenced assets and preserves an in-flight candidate`() {
        val old = submit("old")
        jobs.complete(jobs.claim(lease, 3)!!)
        jobs.activate(old)
        val oldAsset = models.findAll().single().asset
        val current = submit("current", changedBinary = true)
        jobs.complete(jobs.claim(lease, 3)!!)
        jobs.activate(current)
        val currentAsset = models.findAll().single().asset
        val candidate = submit("candidate", changedBinary = true)
        jobs.complete(jobs.claim(lease, 3)!!)
        val candidateAsset = jdbc.queryForObject<String>("SELECT object_path FROM model_asset WHERE sha256 NOT IN (?, ?)",
            oldAsset.sha256, currentAsset.sha256)!!

        val plan = retention.retainCurrentAssets(storageLocation)

        assertTrue(old.toString() in plan.retiredReleaseIds)
        assertTrue(currentAsset.path in plan.protectedPaths)
        assertTrue(candidateAsset in plan.protectedPaths)
        assertTrue(oldAsset.path in plan.obsoletePaths)
        assertNull(jobs.findPublishedAsset(oldAsset, storageLocation))
        assertNotNull(jobs.findPublishedAsset(currentAsset, storageLocation))
        assertEquals("READY", jobs.status(candidate).state)
        assertFailsWith<UploadRejectedException> { jobs.activate(candidate) }
        assertFailsWith<UploadRejectedException> { submit("blocked") }
        retention.finishPruning()
        jobs.activate(candidate)
    }

    @Test fun `completed staging is retained while another job still needs the shared source`() {
        val (path, manifest) = createDataset(root)
        val first = manifest.models.single()
        val second = first.copy(modelId = "fixture_002", asset = first.asset.copy(path = "models/fixture_002.glb"))
        Files.copy(root.resolve(first.asset.path), root.resolve(second.asset.path))
        ManifestWriter.writeAtomic(path, manifest.copy(modelCount = 2, models = listOf(first, second)))
        ModelImportService(jobs, JsonImportManifestReader(), FileModelUploadSpool(root.resolve("spool")), storageLocation).submit(root, path, "shared")
        val job = jobs.claim(lease, 3)!!
        jobs.complete(job)
        assertTrue(jobs.completedSourcePaths().isEmpty())
        assertTrue(Files.exists(job.source))
        jobs.complete(jobs.claim(lease, 3)!!)
        assertEquals(1, CompletedUploadSpool(root.resolve("spool")).remove(jobs.completedSourcePaths()))
        assertFalse(Files.exists(job.source))
    }

    @Test fun `legacy storage location backfill fills only missing entity properties`() {
        val releaseId = submit("legacy")
        jdbc.update(
            "UPDATE model_release SET storage_bucket=?, storage_prefix=NULL WHERE id=?",
            "existing-bucket",
            releaseId,
        )
        val backfill = LegacyStorageLocationBackfill(
            entityManager,
            ModelStorageLocation(bucket = "configured-bucket", prefix = "configured-prefix"),
        )

        TransactionTemplate(transactionManager).executeWithoutResult {
            backfill.afterSingletonsInstantiated()
        }

        assertEquals("existing-bucket", jdbc.queryForObject<String>(
            "SELECT storage_bucket FROM model_release WHERE id=?",
            releaseId,
        ))
        assertEquals("configured-prefix", jdbc.queryForObject<String>(
            "SELECT storage_prefix FROM model_release WHERE id=?",
            releaseId,
        ))
    }

    @Test fun `identical request is idempotent and changed request is rejected`() {
        val id = submit("v1", "request")
        assertEquals(id, submit("v1", "request"))
        assertFailsWith<UploadRejectedException> { submit("v2", "request") }
        assertEquals(1, jdbc.queryForObject<Int>("SELECT COUNT(*) FROM model_upload_job"))
    }

    @Test fun `expired lease is reclaimed and stale worker cannot commit or fail the new job`() {
        submit()
        val old = jobs.claim(lease, 3)!!
        jdbc.update("UPDATE model_upload_job SET lease_until=TIMESTAMP '2000-01-01 00:00:00' WHERE id=?", old.id)
        val fresh = jobs.claim(lease, 3)!!
        assertEquals(old.id, fresh.id)
        assertNotEquals(old.token, fresh.token)
        assertFalse(jobs.complete(old))
        jobs.fail(old, "stale", 1, Duration.ZERO)
        assertTrue(jobs.complete(fresh))
        assertEquals("READY", jobs.status(fresh.releaseId).state)
    }

    @Test fun `two workers cannot claim the same job`() {
        submit()
        Executors.newFixedThreadPool(2).use { executor ->
            val claims = executor.invokeAll(List(2) { Callable { jobs.claim(lease, 3) } })
                .map { it.get() }
            assertEquals(1, claims.count { it != null })
        }
    }

    @Test fun `S3 success followed by DB rollback retries without publishing partial metadata`() {
        val id = submit()
        val remote = mutableSetOf<String>()
        var puts = 0
        val publisher = PublishModelAsset { asset, _, _ -> if (remote.add(asset.path)) puts++ }
        val failing = object : ModelUploadQueue by jobs {
            override fun complete(job: UploadJob): Boolean = TransactionTemplate(transactionManager).execute<Boolean> {
                jobs.complete(job)
                throw IllegalStateException("simulated DB rollback")
            }
        }
        ModelUploadWorker(failing, publisher, lease, 3, Duration.ZERO).runOnce()
        assertEquals(1, puts)
        assertEquals(0, jdbc.queryForObject<Int>("SELECT COUNT(*) FROM model_content"))
        assertEquals(0, jobs.status(id).succeeded)
        ModelUploadWorker(jobs, publisher, lease, 3, Duration.ZERO).runOnce()
        assertEquals(1, puts)
        assertEquals("READY", jobs.status(id).state)
    }

    @Test fun `failed new release preserves active release and can be explicitly retried`() {
        val current = submit("current")
        jobs.complete(jobs.claim(lease, 3)!!)
        jobs.activate(current)
        val next = submit("next", changedBinary = true)
        val failed = jobs.claim(lease, 1)!!
        jobs.fail(failed, "UNAVAILABLE", 1, Duration.ZERO)
        assertEquals("FAILED", jobs.status(next).state)
        assertFailsWith<UploadRejectedException> { jobs.activate(next) }
        assertEquals("current", models.datasetVersion)
        jobs.retry(next)
        jobs.complete(jobs.claim(lease, 3)!!)
        jobs.activate(next)
        assertEquals("next", models.datasetVersion)
    }

    @Test fun `concurrent releases cannot silently replace a newer activation`() {
        val first = submit("first")
        val second = submit("second")
        repeat(2) { jobs.complete(jobs.claim(lease, 3)!!) }
        jobs.activate(first)
        assertFailsWith<UploadRejectedException> { jobs.activate(second) }
        assertEquals("first", models.datasetVersion)
    }

    @Test fun `partial release remains hidden and cannot activate when another file fails`() {
        val (path, manifest) = createDataset(root)
        val first = manifest.models.single()
        val second = first.copy(modelId = "fixture_002", asset = first.asset.copy(path = "models/fixture_002.glb"))
        Files.copy(root.resolve(first.asset.path), root.resolve(second.asset.path))
        ManifestWriter.writeAtomic(path, manifest.copy(modelCount = 2, models = listOf(first, second)))
        val id = ModelImportService(jobs, JsonImportManifestReader(), FileModelUploadSpool(root.resolve("spool")), storageLocation).submit(root, path, "partial")
        jobs.complete(jobs.claim(lease, 1)!!)
        assertEquals(1, jobs.status(id).succeeded)
        assertEquals("UPLOADING", jobs.status(id).state)
        assertTrue(models.findAll().isEmpty())
        jobs.fail(jobs.claim(lease, 1)!!, "UNAVAILABLE", 1, Duration.ZERO)
        assertEquals(listOf("UNAVAILABLE"), jobs.status(id).errors)
        assertFailsWith<UploadRejectedException> { jobs.activate(id) }
        assertTrue(models.findAll().isEmpty())
    }

    @Test fun `concurrent identical imports register one release and one job`() {
        val (path, _) = createDataset(root)
        Executors.newFixedThreadPool(2).use { executor ->
            val ids = executor.invokeAll(List(2) { Callable {
                ModelImportService(jobs, JsonImportManifestReader(), FileModelUploadSpool(root.resolve("spool")), storageLocation).submit(root, path, "same-request")
            } })
                .map { it.get() }
            assertEquals(ids[0], ids[1])
        }
        assertEquals(1, jdbc.queryForObject<Int>("SELECT COUNT(*) FROM model_release"))
        assertEquals(1, jdbc.queryForObject<Int>("SELECT COUNT(*) FROM model_upload_job"))
    }

    @Test fun `crashed final attempt is marked failed rather than stuck forever`() {
        val id = submit()
        jobs.claim(lease, 1)
        jdbc.update("UPDATE model_upload_job SET lease_until=TIMESTAMP '2000-01-01 00:00:00'")
        assertNull(jobs.claim(lease, 1))
        assertEquals("FAILED", jobs.status(id).state)
    }
}
