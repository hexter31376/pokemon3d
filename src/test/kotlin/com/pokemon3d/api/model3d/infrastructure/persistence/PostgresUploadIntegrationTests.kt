package com.pokemon3d.api.model3d.infrastructure.persistence

import com.pokemon3d.api.model3d.application.ModelUploadWorker
import com.pokemon3d.api.model3d.domain.ModelStorageLocation
import com.pokemon3d.api.model3d.infrastructure.importer.ManifestDatabaseImporter
import com.pokemon3d.api.model3d.infrastructure.storage.s3.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.sql.DriverManager
import java.time.Duration
import java.util.UUID
import kotlin.test.*

@Tag("databaseIntegration")
@SpringBootTest(properties = ["pokemon.uploads.enabled=false", "pokemon.storage.s3.enabled=false"])
class PostgresUploadIntegrationTests : ModelUploadDatabaseTests() {
    companion object {
        private val schema = "test_" + UUID.randomUUID().toString().replace("-", "")
        private val url = requireNotNull(System.getenv("MODEL_DATABASE_URL")) { "Set MODEL_DATABASE_URL" }
        private val user = requireNotNull(System.getenv("MODEL_DATABASE_USERNAME"))
        private val password = requireNotNull(System.getenv("MODEL_DATABASE_PASSWORD"))
        @JvmStatic @DynamicPropertySource
        fun database(registry: DynamicPropertyRegistry) {
            DriverManager.getConnection(url, user, password).use { connection ->
                connection.createStatement().use { it.execute("CREATE SCHEMA $schema") }
            }
            registry.add("spring.datasource.url") { url + (if ('?' in url) "&" else "?") + "currentSchema=$schema" }
            registry.add("spring.datasource.username") { user }
            registry.add("spring.datasource.password") { password }
            registry.add("spring.flyway.default-schema") { schema }
        }
        @JvmStatic @AfterAll fun removeSchema() {
            DriverManager.getConnection(url, user, password).use { connection ->
                connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
            }
        }
    }

    @Test fun `actual S3 bytes verified before database activation and retries preserve object`() {
        val (path, _) = createDataset(root)
        val env = System.getenv().toMutableMap()
        env["MODEL_STORAGE_BUCKET"] = "test-${UUID.randomUUID()}"
        val props = S3StorageProperties.fromEnvironment(env)
        val location = ModelStorageLocation(props.bucket, props.prefix)
        val id = ManifestDatabaseImporter(jobs, root.resolve("spool"), location).submit(root, path, "s3-test")
        S3ClientFactory.create(props).use { client ->
            client.createBucket { it.bucket(props.bucket) }
            var key: String? = null
            try {
                val publisher = S3ModelAssetPublisher(client, props)
                val worker = ModelUploadWorker(jobs, publisher, Duration.ofMinutes(10), 3, Duration.ZERO)
                assertTrue(worker.runOnce())
                assertEquals("READY", jobs.status(id).state)
                assertTrue(models.findAll().isEmpty())
                jobs.activate(id)
                val model = models.findAll().single()
                key = ObjectKeyResolver(props.prefix).resolve(model.asset.path)
                val store = S3ObjectStore(client, props.bucket)
                assertEquals(ContentDigest(model.asset.sizeBytes, model.asset.sha256), store.contentDigest(key))
                // A retry after the DB commit is lost can verify the already uploaded object without local data.
                publisher.publish(model, root.resolve("no-longer-present.glb"), location)
                // Conditional PUT is enforced by this real S3-compatible server.
                val collision = assertFailsWith<software.amazon.awssdk.services.s3.model.S3Exception> {
                    client.putObject({ it.bucket(props.bucket).key(key).ifNoneMatch("*") },
                        software.amazon.awssdk.core.sync.RequestBody.fromString("must not replace"))
                }
                assertEquals(412, collision.statusCode())
                assertEquals(model.asset.sha256, store.contentDigest(key).sha256)
            } finally {
                key?.let { value -> client.deleteObject { it.bucket(props.bucket).key(value) } }
                client.deleteBucket { it.bucket(props.bucket) }
            }
        }
    }
}
