package com.pokemon3d.api.assets.infrastructure.storage.s3

import com.pokemon3d.api.assets.domain.ModelAsset
import com.pokemon3d.api.assets.domain.ModelAssetStorage
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.assets.domain.StoredModelAsset
import com.pokemon3d.api.assets.domain.exception.ModelStorageException
import com.pokemon3d.api.assets.domain.exception.StorageFailure
import com.pokemon3d.api.catalog.application.ModelAssetNotFoundException
import com.pokemon3d.api.catalog.application.ModelNotFoundException
import com.pokemon3d.api.catalog.application.ModelResourceService
import com.pokemon3d.api.catalog.domain.ModelMetadataRepository
import com.pokemon3d.api.catalog.domain.VersionedModel
import com.pokemon3d.api.catalog.presentation.ModelResourceExceptionHandler
import com.pokemon3d.api.global.exception.GlobalExceptionHandler
import com.pokemon3d.api.publishing.infrastructure.manifest.JsonManifestModelMetadataRepository
import java.net.URI
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*

internal fun properties(endpoint: String = "http://internal-storage:8333", publicUrl: String = "https://assets.example.test/bucket") =
    S3StorageProperties(
        true,
        endpoint,
        publicUrl,
        "us-east-1",
        "test-bucket",
        "pokemon",
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
    )

class StorageUnitTests {
    @Test
    fun `public URLs use explicit browser base and encode each segment once`() {
        val keys = ObjectKeyResolver("/pokemon/")
        val urls = PublicAssetUrlResolver("https://assets.example.test/bucket/")
        val result = urls.resolve(keys.resolve("models/피카 츄#?.glb"))
        assertEquals("https://assets.example.test/bucket/pokemon/models/%ED%94%BC%EC%B9%B4%20%EC%B8%84%23%3F.glb", result.toString())
        val client = mock(S3Client::class.java)
        val storage = S3ModelAssetStorage(client, properties())
        assertEquals("assets.example.test", storage.publicUrl(
            ModelAsset(
                "models/a.glb",
                1,
                "a".repeat(64),
                emptyList()
            ),
            ModelStorageLocation("test-bucket", "pokemon"),
        ).host)
        verifyNoInteractions(client)
    }

    @Test
    fun `unsafe paths and credential bearing URL configurations are rejected`() {
        for (key in listOf("../a.glb", "/a.glb", "a//b.glb", "a/./b.glb", "a\\b", "%2e%2e/a")) {
            assertFailsWith<IllegalArgumentException> { ObjectKeyResolver("").resolve(key) }
        }
        for (url in listOf("ftp://host/bucket", "https://user:password@host/bucket", "https://host/bucket?token=x", "https://host/a/../b")) {
            assertFailsWith<IllegalArgumentException> { PublicAssetUrlResolver(url) }
        }
        assertFailsWith<IllegalArgumentException> { S3StorageProperties(enabled = true).validate() }
        val propertyText = properties().toString()
        assertFalse(propertyText.contains("secretKey"))
    }

    @Test
    fun `missing object is null only after bucket check succeeds`() {
        val client = mock(S3Client::class.java)
        `when`(client.headObject(any(HeadObjectRequest::class.java)))
            .thenThrow(
                S3Exception.builder()
                    .statusCode(404)
                    .build(),
            )
        `when`(client.headBucket(any(HeadBucketRequest::class.java)))
            .thenReturn(HeadBucketResponse.builder().build())
        assertNull(S3ObjectStore(client, "bucket").head("missing"))
        val verifiedClient = verify(client)
        verifiedClient.headBucket(any(HeadBucketRequest::class.java))
    }

    @Test
    fun `missing bucket authorization and connection failures stay distinct`() {
        val client = mock(S3Client::class.java)
        val objects = S3ObjectStore(client, "bucket")
        `when`(client.headObject(any(HeadObjectRequest::class.java)))
            .thenThrow(
                S3Exception.builder()
                    .statusCode(404)
                    .build(),
            )
        `when`(client.headBucket(any(HeadBucketRequest::class.java)))
            .thenThrow(
                S3Exception.builder()
                    .statusCode(404)
                    .build(),
            )
        assertEquals(StorageFailure.BUCKET_NOT_FOUND, assertFailsWith<ModelStorageException> { objects.head("x") }.failure)
        `when`(client.headObject(any(HeadObjectRequest::class.java)))
            .thenThrow(
                S3Exception.builder()
                    .statusCode(403)
                    .build(),
            )
        assertEquals(StorageFailure.ACCESS_DENIED, assertFailsWith<ModelStorageException> { objects.head("x") }.failure)
        `when`(client.headObject(any(HeadObjectRequest::class.java)))
            .thenThrow(SdkClientException.create("connection failed"))
        assertEquals(StorageFailure.UNAVAILABLE, assertFailsWith<ModelStorageException> { objects.head("x") }.failure)
    }

    @Test
    fun `application combines metadata and URL and rejects missing or inconsistent storage`() {
        val manifestPath = Path.of(javaClass.getResource("/manifest/valid.json")!!.toURI())
        val manifestRepository = JsonManifestModelMetadataRepository(manifestPath)
        val location = ModelStorageLocation("fixture-bucket", "fixture-prefix")
        val repository = object : ModelMetadataRepository by manifestRepository {
            override fun findVersionedById(modelId: String) = manifestRepository.findById(modelId)?.let {
                VersionedModel(manifestRepository.datasetVersion, it, location)
            }
        }
        val asset = repository.findAll()
            .single()
            .asset
        val storage = mock(ModelAssetStorage::class.java)
        val service = ModelResourceService(repository, storage)
        assertFailsWith<ModelNotFoundException> { service.findById("absent") }
        assertFailsWith<ModelAssetNotFoundException> { service.findById("fixture_001") }
        `when`(storage.find(asset, location))
            .thenReturn(StoredModelAsset(asset.sizeBytes, "text/plain", asset.sha256))
        assertEquals(StorageFailure.INTEGRITY_MISMATCH, assertFailsWith<ModelStorageException> { service.findById("fixture_001") }.failure)
        `when`(storage.find(asset, location))
            .thenReturn(StoredModelAsset(asset.sizeBytes, "model/gltf-binary", asset.sha256))
        `when`(storage.publicUrl(asset, location))
            .thenReturn(URI("https://assets.example.test/a.glb"))
        assertEquals("fixture-v1", service.findById("fixture_001").datasetVersion)
    }

    @Test
    fun `disabled storage creates no client and enabled invalid settings fail fast`() {
        val runner = ApplicationContextRunner()
            .withUserConfiguration(S3StorageConfiguration::class.java)
        runner.run { assertNull(it.startupFailure); assertFalse(it.containsBean("modelS3Client")) }
        runner.withPropertyValues("pokemon.storage.s3.enabled=true")
            .run { assertNotNull(it.startupFailure) }
    }

    @Test
    fun `storage exceptions use public error codes instead of leaking internal failures`() {
        val mvc = MockMvcBuilders.standaloneSetup(FailureController())
            .setControllerAdvice(ModelResourceExceptionHandler(), GlobalExceptionHandler())
            .build()
        mvc.perform(get("/storage-failure"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.error.code").value("MODEL3D_003"))
            .andExpect(jsonPath("$.error.message").value("모델 저장소를 이용할 수 없습니다."))
        mvc.perform(get("/asset-missing"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("MODEL3D_002"))
    }

    @RestController
    class FailureController {
        @GetMapping("/storage-failure")
        fun failure(): Unit = throw ModelStorageException(
            StorageFailure.ACCESS_DENIED,
            "private/key",
            IllegalStateException("private error details")
        )
        @GetMapping("/asset-missing")
        fun missing(): Unit = throw ModelAssetNotFoundException("missing")
    }
}
