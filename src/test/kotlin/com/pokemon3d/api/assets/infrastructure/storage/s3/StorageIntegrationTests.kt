package com.pokemon3d.api.assets.infrastructure.storage.s3

import com.pokemon3d.api.assets.application.ContentDigest
import com.pokemon3d.api.assets.application.digest
import com.pokemon3d.api.assets.domain.ModelAssetRetention
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@Tag("storageIntegration")
class StorageIntegrationTests {
    @TempDir lateinit var root: Path

    @Test
    fun `real storage preserves bytes and supports repeated uploads and public model reads`() {
        val env = System.getenv().toMutableMap()
        val bucket = "integration-${UUID.randomUUID()}"
        env["MODEL_STORAGE_BUCKET"] = bucket
        env["MODEL_STORAGE_PUBLIC_BASE_URL"] = env.getValue("MODEL_STORAGE_ENDPOINT")
            .trimEnd('/') + "/$bucket"
        val properties = S3StorageProperties.fromEnvironment(env)
        val keys = ObjectKeyResolver(properties.prefix)
        val location = ModelStorageLocation(properties.bucket, properties.prefix)
        val (manifestPath, manifest) = createDataset(root)
        val originalModel = manifest.models.single()
        val asset = originalModel.asset.copy(path = "objects/sha256/${originalModel.asset.sha256}.glb")
        val model = originalModel.copy(asset = asset)
        val source = root.resolve(originalModel.asset.path)
        S3ClientFactory.create(properties)
            .use { client ->
            client.createBucket { it.bucket(bucket) }
            try {
                val store = S3ObjectStore(client, bucket)
                val publisher = S3ModelAssetPublisher(client, properties)
                publisher.publish(model.asset, source, location)
                publisher.publish(model.asset, source, location)
                assertEquals(
                    ContentDigest(asset.sizeBytes, asset.sha256),
                    store.contentDigest(keys.resolve(asset.path)),
                )
                val manifestDigest = Files.newInputStream(manifestPath)
                    .use(::digest)
                    .sha256
                store.put(
                    keys.resolve("manifest.json"),
                    manifestPath,
                    "application/json",
                    manifestDigest,
                )
                val adapter = S3ModelAssetStorage(client, properties)
                assertTrue(adapter.find(asset, location)!!.matches(asset))
                assertNull(adapter.find(asset.copy(path = "models/missing.glb"), location))
                // Public access is an explicit bucket policy, independent of the SDK credentials.
                PublicModelAccessConfigurer(client, properties).configure()
                val obsolete = keys.resolve("releases/old/models/obsolete/file.glb")
                store.put(obsolete, source, "model/gltf-binary", asset.sha256)
                val pruned = S3ObsoleteModelPruner(client, properties).prune(
                    ModelAssetRetention(setOf(asset.path), emptySet(), setOf("old")),
                )
                assertEquals(1, pruned.deletedObjects)
                assertNull(store.head(obsolete))
                assertNotNull(store.head(keys.resolve(asset.path)))
                assertNotNull(store.head(keys.resolve("manifest.json")))
                HttpClient.newHttpClient()
                    .use { http ->
                    val request = HttpRequest.newBuilder(adapter.publicUrl(asset, location))
                        .GET()
                        .build()
                    val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
                    assertEquals(200, response.statusCode())
                    val contentType = response.headers()
                        .firstValue("Content-Type")
                        .orElseThrow()
                    assertEquals("model/gltf-binary", contentType)
                    assertContentEquals(Files.readAllBytes(source), response.body())
                    val privateManifest = java.net.URI(properties.publicBaseUrl + "/" + keys.resolve("manifest.json"))
                    val privateRequest = HttpRequest.newBuilder(privateManifest)
                        .GET()
                        .build()
                    val privateResponse = http.send(
                        privateRequest,
                        HttpResponse.BodyHandlers.discarding(),
                    )
                    assertEquals(403, privateResponse.statusCode())
                }
            } finally {
                client.deleteObject {
                    it.bucket(bucket)
                        .key(keys.resolve(asset.path))
                }
                client.deleteObject {
                    it.bucket(bucket)
                        .key(keys.resolve("manifest.json"))
                }
                client.deleteBucket { it.bucket(bucket) }
            }
        }
    }
}
