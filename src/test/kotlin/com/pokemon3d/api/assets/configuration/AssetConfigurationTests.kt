package com.pokemon3d.api.assets.configuration

import com.pokemon3d.api.assets.application.port.input.FindModelAsset
import com.pokemon3d.api.assets.application.port.input.PublishModelAsset
import com.pokemon3d.api.assets.domain.ModelAsset
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.assets.domain.exception.ModelStorageException
import com.pokemon3d.api.assets.domain.exception.StorageFailure
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class AssetConfigurationTests {
    private val runner = ApplicationContextRunner()
        .withUserConfiguration(S3StorageConfiguration::class.java, ModelAssetQueryConfiguration::class.java)

    @Test
    fun `disabled storage keeps the query port available and reports unavailable on use`() {
        runner.run { context ->
            assertNull(context.startupFailure)
            assertFalse(context.containsBean("modelS3Client"))
            val query = context.getBean(FindModelAsset::class.java)
            val error = assertFailsWith<ModelStorageException> {
                query.findUrl(ModelAsset("model.glb", 1, "a".repeat(64), emptyList()), ModelStorageLocation("bucket", ""))
            }
            assertEquals(StorageFailure.UNAVAILABLE, error.failure)
        }
    }

    @Test
    fun `enabled storage wires query and publication ports and exposes only location to other modules`() {
        runner.withPropertyValues(
            "pokemon.storage.s3.enabled=true",
            "pokemon.storage.s3.endpoint=http://127.0.0.1:8333",
            "pokemon.storage.s3.public-base-url=https://assets.example.test/bucket",
            "pokemon.storage.s3.region=us-east-1",
            "pokemon.storage.s3.bucket=test-bucket",
            "pokemon.storage.s3.prefix=models",
            "pokemon.storage.s3.access-key=test-access",
            "pokemon.storage.s3.secret-key=test-secret",
        ).run { context ->
            assertNull(context.startupFailure)
            assertTrue(context.getBeansOfType(FindModelAsset::class.java).size == 1)
            assertTrue(context.getBeansOfType(PublishModelAsset::class.java).size == 1)
            assertEquals(ModelStorageLocation("test-bucket", "models"), context.getBean(ModelStorageLocation::class.java))
        }
    }
}
