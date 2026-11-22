package com.pokemon3d.api.assets.adapter.output.s3

import com.pokemon3d.api.assets.application.port.output.ModelAssetUploadStore
import com.pokemon3d.api.assets.domain.ContentDigest
import com.pokemon3d.api.assets.domain.ModelAsset
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.assets.domain.StoredModelAsset
import com.pokemon3d.api.assets.domain.exception.ModelStorageException
import com.pokemon3d.api.assets.domain.exception.StorageFailure
import java.nio.file.Path
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.S3Exception

class S3ModelAssetUploadStore(
    private val client: S3Client,
    private val properties: S3StorageProperties,
) : ModelAssetUploadStore {
    override fun find(asset: ModelAsset, location: ModelStorageLocation): StoredModelAsset? =
        S3ObjectStore(client, location.bucket).head(key(asset, location))

    override fun contentDigest(asset: ModelAsset, location: ModelStorageLocation): ContentDigest =
        S3ObjectStore(client, location.bucket).contentDigest(key(asset, location))

    override fun uploadIfAbsent(asset: ModelAsset, source: Path, location: ModelStorageLocation) {
        val key = key(asset, location)
        try {
            client.putObject(
                {
                    it.bucket(location.bucket)
                        .key(key)
                        .contentType("model/gltf-binary")
                        .metadata(mapOf("sha256" to asset.sha256))
                        .ifNoneMatch("*")
                },
                RequestBody.fromFile(source),
            )
        } catch (ex: S3Exception) {
            // The application verifies the winner's bytes after a conditional-write conflict.
            if (ex.statusCode() != 412) throw ModelStorageException(StorageFailure.UNAVAILABLE, key, ex)
        }
    }

    private fun key(asset: ModelAsset, location: ModelStorageLocation): String {
        if (location.bucket != properties.bucket) {
            throw ModelStorageException(
                StorageFailure.CONFIGURATION_MISMATCH,
                null,
                IllegalStateException("Release bucket ${location.bucket} differs from configured bucket ${properties.bucket}"),
            )
        }
        return ObjectKeyResolver(location.prefix).resolve(asset.path)
    }
}
