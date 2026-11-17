package com.pokemon3d.api.model3d.infrastructure.storage.s3

import com.pokemon3d.api.model3d.domain.ModelStorageLocation
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.*

@Tag("storageIntegration")
class StorageIntegrationTests {
    @TempDir lateinit var root: Path

    @Test
    fun `real storage preserves bytes and supports repeated uploads and public model reads`() {
        val env = System.getenv().toMutableMap()
        val bucket = "integration-${UUID.randomUUID()}"
        env["MODEL_STORAGE_BUCKET"] = bucket
        env["MODEL_STORAGE_PUBLIC_BASE_URL"] = env.getValue("MODEL_STORAGE_ENDPOINT").trimEnd('/') + "/$bucket"
        val properties = S3StorageProperties.fromEnvironment(env)
        val keys = ObjectKeyResolver(properties.prefix)
        val location = ModelStorageLocation(properties.bucket, properties.prefix)
        val (manifestPath, manifest) = createDataset(root)
        val asset = manifest.models.single().asset
        S3ClientFactory.create(properties).use { client ->
            client.createBucket { it.bucket(bucket) }
            try {
                val store = S3ObjectStore(client, bucket)
                val publisher = S3ModelAssetPublisher(client, properties)
                publisher.publish(manifest.models.single(), root.resolve(asset.path), location)
                publisher.publish(manifest.models.single(), root.resolve(asset.path), location)
                assertEquals(ContentDigest(asset.sizeBytes, asset.sha256), store.contentDigest(keys.resolve(asset.path)))
                store.put(keys.resolve("manifest.json"), manifestPath, "application/json", Files.newInputStream(manifestPath).use(::digest).sha256)
                val adapter = S3ModelAssetStorage(client, properties)
                assertTrue(adapter.find(asset, location)!!.matches(asset))
                assertNull(adapter.find(asset.copy(path = "models/missing.glb"), location))
                // Public access is an explicit bucket policy, independent of the SDK credentials.
                val resource = "arn:aws:s3:::$bucket/${keys.resolve("models")}/*"
                client.putBucketPolicy { it.bucket(bucket).policy("""{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":"s3:GetObject","Resource":"$resource"}]}""") }
                HttpClient.newHttpClient().use { http ->
                    val response = http.send(HttpRequest.newBuilder(adapter.publicUrl(asset, location)).GET().build(), HttpResponse.BodyHandlers.ofByteArray())
                    assertEquals(200, response.statusCode())
                    assertEquals("model/gltf-binary", response.headers().firstValue("Content-Type").orElseThrow())
                    assertContentEquals(Files.readAllBytes(root.resolve(asset.path)), response.body())
                    val privateManifest = java.net.URI(properties.publicBaseUrl + "/" + keys.resolve("manifest.json"))
                    assertEquals(403, http.send(HttpRequest.newBuilder(privateManifest).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode())
                }
            } finally {
                client.deleteObject { it.bucket(bucket).key(keys.resolve(asset.path)) }
                client.deleteObject { it.bucket(bucket).key(keys.resolve("manifest.json")) }
                client.deleteBucket { it.bucket(bucket) }
            }
        }
    }
}
