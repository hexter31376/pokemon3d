package com.pokemon3d.api.model3d.infrastructure.persistence

import com.pokemon3d.api.model3d.application.ModelUploadWorker
import com.pokemon3d.api.model3d.domain.*
import com.pokemon3d.api.model3d.infrastructure.importer.ManifestDatabaseImporter
import com.pokemon3d.api.model3d.infrastructure.manifest.tools.ManifestWriter
import com.pokemon3d.api.model3d.infrastructure.storage.s3.createDataset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:uploads;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "pokemon.uploads.enabled=false", "pokemon.storage.s3.enabled=false",
])
open class ModelUploadDatabaseTests {
    @Autowired lateinit var jobs: ModelUploadRepository
    @Autowired lateinit var models: ModelMetadataRepository
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var transactionManager: PlatformTransactionManager
    @TempDir lateinit var root: Path
    private val lease = Duration.ofMinutes(10)

    @BeforeEach fun clean() {
        jdbc.update("UPDATE model_active_release SET release_id=NULL,revision=0")
        jdbc.update("DELETE FROM model_metadata")
        jdbc.update("DELETE FROM model_upload_job")
        jdbc.update("DELETE FROM model_release")
    }

    private fun submit(version: String = UUID.randomUUID().toString(), key: String = version): UUID {
        val directory = Files.createDirectories(root.resolve(version))
        val (path, manifest) = createDataset(directory)
        ManifestWriter.writeAtomic(path, manifest.copy(datasetVersion = version))
        return ManifestDatabaseImporter(jobs, root.resolve("spool")).submit(directory, path, key)
    }

    @Test fun `stages immutable files and publishes only after verified completion and activation`() {
        val id = submit("v1")
        assertTrue(models.findAll().isEmpty())
        assertFailsWith<UploadRejectedException> { jobs.activate(id) }
        val job = jobs.claim(lease, 3)!!
        assertTrue(job.model.asset.path.startsWith("releases/$id/"))
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

    @Test fun `identical request is idempotent and changed request is rejected`() {
        val id = submit("v1", "request")
        assertEquals(id, submit("v1", "request"))
        assertFailsWith<UploadRejectedException> { submit("v2", "request") }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM model_upload_job", Int::class.java))
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
            val claims = executor.invokeAll(List(2) { Callable { jobs.claim(lease, 3) } }).map { it.get() }
            assertEquals(1, claims.count { it != null })
        }
    }

    @Test fun `S3 success followed by DB rollback retries without publishing partial metadata`() {
        val id = submit()
        val remote = mutableSetOf<String>()
        var puts = 0
        val publisher = ModelAssetPublisher { model, _ -> if (remote.add(model.asset.path)) puts++ }
        val failing = object : ModelUploadRepository by jobs {
            override fun complete(job: UploadJob): Boolean = TransactionTemplate(transactionManager).execute<Boolean> {
                jobs.complete(job)
                throw IllegalStateException("simulated DB rollback")
            }
        }
        ModelUploadWorker(failing, publisher, lease, 3, Duration.ZERO).runOnce()
        assertEquals(1, puts)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM model_metadata", Int::class.java))
        assertEquals(0, jobs.status(id).succeeded)
        ModelUploadWorker(jobs, publisher, lease, 3, Duration.ZERO).runOnce()
        assertEquals(1, puts)
        assertEquals("READY", jobs.status(id).state)
    }

    @Test fun `failed new release preserves active release and can be explicitly retried`() {
        val current = submit("current")
        jobs.complete(jobs.claim(lease, 3)!!)
        jobs.activate(current)
        val next = submit("next")
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
        val id = ManifestDatabaseImporter(jobs, root.resolve("spool")).submit(root, path, "partial")
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
                ManifestDatabaseImporter(jobs, root.resolve("spool")).submit(root, path, "same-request")
            } }).map { it.get() }
            assertEquals(ids[0], ids[1])
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM model_release", Int::class.java))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM model_upload_job", Int::class.java))
    }

    @Test fun `crashed final attempt is marked failed rather than stuck forever`() {
        val id = submit()
        jobs.claim(lease, 1)
        jdbc.update("UPDATE model_upload_job SET lease_until=TIMESTAMP '2000-01-01 00:00:00'")
        assertNull(jobs.claim(lease, 1))
        assertEquals("FAILED", jobs.status(id).state)
    }
}
