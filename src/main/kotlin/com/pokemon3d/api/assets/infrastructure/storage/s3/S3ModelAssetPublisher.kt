package com.pokemon3d.api.assets.infrastructure.storage.s3

import com.pokemon3d.api.assets.application.ContentDigest
import com.pokemon3d.api.assets.application.digest
import com.pokemon3d.api.assets.domain.ModelAsset
import com.pokemon3d.api.assets.domain.ModelAssetPublisher
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.assets.domain.exception.ModelStorageException
import com.pokemon3d.api.assets.domain.exception.StorageFailure
import java.nio.file.Files
import java.nio.file.Path
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.S3Exception

class S3ModelAssetPublisher(
    private val client: S3Client,
    private val properties: S3StorageProperties,
) : ModelAssetPublisher {
    override fun publish(asset: ModelAsset, source: Path, storageLocation: ModelStorageLocation) {
        requireCompatibleBucket(storageLocation)
        val key = ObjectKeyResolver(storageLocation.prefix)
            .resolve(asset.path)
        val objects = S3ObjectStore(client, storageLocation.bucket)
        val expected = ContentDigest(asset.sizeBytes, asset.sha256)
        // Recovery after a successful PUT but failed DB commit does not need to upload again.
        if (objects.head(key) != null) {
            verify(objects, asset, key, expected)
            return
        }
        val sourceDigest = Files.newInputStream(source)
            .use(::digest)
        require(sourceDigest == expected) {
            "Staged GLB differs from expected bytes"
        }
        try {
            client.putObject(
                {
                    it.bucket(storageLocation.bucket)
                        .key(key)
                        .contentType("model/gltf-binary")
                        .metadata(mapOf("sha256" to asset.sha256))
                        .ifNoneMatch("*")
                },
                RequestBody.fromFile(source),
            )
        } catch (ex: S3Exception) {
            // Another retry may have completed the same immutable object first.
            if (ex.statusCode() != 412) throw ModelStorageException(StorageFailure.UNAVAILABLE, key, ex)
        }
        verify(objects, asset, key, expected)
    }

    private fun verify(objects: S3ObjectStore, asset: ModelAsset, key: String, expected: ContentDigest) {
        if (objects.head(key)?.matches(asset) != true || objects.contentDigest(key) != expected) {
            throw ModelStorageException(StorageFailure.INTEGRITY_MISMATCH, key)
        }
    }

    private fun requireCompatibleBucket(location: ModelStorageLocation) {
        if (location.bucket != properties.bucket) {
            throw ModelStorageException(
                StorageFailure.CONFIGURATION_MISMATCH,
                null,
                IllegalStateException(
                    "Release bucket ${location.bucket} differs from configured bucket ${properties.bucket}",
                ),
            )
        }
    }
}
