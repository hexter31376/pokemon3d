package com.pokemon3d.api.assets.application.service

import com.pokemon3d.api.assets.application.port.output.ModelAssetUploadStore
import com.pokemon3d.api.assets.domain.ContentDigest
import com.pokemon3d.api.assets.domain.ModelAsset
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.assets.domain.StoredModelAsset
import com.pokemon3d.api.assets.domain.exception.ModelStorageException
import com.pokemon3d.api.assets.domain.exception.StorageFailure
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class ModelAssetPublicationServiceTests {
    private val asset = ModelAsset("objects/sha256/model.glb", 12, "a".repeat(64), emptyList())
    private val location = ModelStorageLocation("bucket", "prefix")
    private val expected = ContentDigest(asset.sizeBytes, asset.sha256)
    private val validMetadata = StoredModelAsset(asset.sizeBytes, "model/gltf-binary", asset.sha256)

    @Test
    fun `retry verifies an existing object without reading the removed local source`() {
        val storage = MemoryUploadStore(validMetadata, expected)
        val service = ModelAssetPublicationService(storage) { error("Source must not be read") }
        service.publish(asset, Path.of("missing.glb"), location)
        assertEquals(0, storage.uploads)
        assertEquals(1, storage.digestReads)
    }

    @Test
    fun `changed local bytes are rejected before uploading`() {
        val storage = MemoryUploadStore(null, expected)
        val service = ModelAssetPublicationService(storage) { expected.copy(sizeBytes = 99) }
        assertFailsWith<IllegalArgumentException> { service.publish(asset, Path.of("source.glb"), location) }
        assertEquals(0, storage.uploads)
        assertEquals(0, storage.digestReads)
    }

    @Test
    fun `successful publication verifies bytes after the conditional upload`() {
        val storage = MemoryUploadStore(null, expected)
        val service = ModelAssetPublicationService(storage) { expected }
        service.publish(asset, Path.of("source.glb"), location)
        assertEquals(1, storage.uploads)
        assertEquals(1, storage.digestReads)
    }

    @Test
    fun `remote byte corruption is rejected even when object metadata matches`() {
        val storage = MemoryUploadStore(validMetadata, expected.copy(sha256 = "b".repeat(64)))
        val service = ModelAssetPublicationService(storage) { error("Source must not be read") }
        val failure = assertFailsWith<ModelStorageException> { service.publish(asset, Path.of("source.glb"), location) }
        assertEquals(StorageFailure.INTEGRITY_MISMATCH, failure.failure)
        assertEquals(0, storage.uploads)
    }

    @Test
    fun `a conflicting concurrent upload is verified rather than overwritten`() {
        val storage = MemoryUploadStore(null, expected.copy(sha256 = "b".repeat(64)))
        val service = ModelAssetPublicationService(storage) { expected }
        val failure = assertFailsWith<ModelStorageException> { service.publish(asset, Path.of("source.glb"), location) }
        assertEquals(StorageFailure.INTEGRITY_MISMATCH, failure.failure)
        assertEquals(1, storage.uploads)
    }

    private inner class MemoryUploadStore(
        private var metadata: StoredModelAsset?,
        private val remoteDigest: ContentDigest,
    ) : ModelAssetUploadStore {
        var uploads = 0
        var digestReads = 0
        override fun find(asset: ModelAsset, location: ModelStorageLocation): StoredModelAsset? = metadata
        override fun uploadIfAbsent(asset: ModelAsset, source: Path, location: ModelStorageLocation) {
            uploads++
            metadata = validMetadata
        }
        override fun contentDigest(asset: ModelAsset, location: ModelStorageLocation): ContentDigest {
            digestReads++
            return remoteDigest
        }
    }
}
