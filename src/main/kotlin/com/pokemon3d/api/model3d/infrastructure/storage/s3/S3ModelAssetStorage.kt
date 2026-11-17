package com.pokemon3d.api.model3d.infrastructure.storage.s3

import com.pokemon3d.api.model3d.domain.ModelAsset
import com.pokemon3d.api.model3d.domain.ModelAssetStorage
import com.pokemon3d.api.model3d.domain.ModelStorageLocation
import com.pokemon3d.api.model3d.domain.StoredModelAsset
import com.pokemon3d.api.model3d.domain.exception.ModelStorageException
import com.pokemon3d.api.model3d.domain.exception.StorageFailure
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

class S3ModelAssetStorage(private val client: S3Client, properties: S3StorageProperties) : ModelAssetStorage {
    private val urls = PublicAssetUrlResolver(properties.publicBaseUrl)
    private val configuredBucket = properties.bucket

    override fun find(asset: ModelAsset, storageLocation: ModelStorageLocation): StoredModelAsset? {
        requireCompatibleBucket(storageLocation, asset)
        val objectKey = ObjectKeyResolver(storageLocation.prefix)
            .resolve(asset.path)
        return S3ObjectStore(client, storageLocation.bucket)
            .head(objectKey)
    }

    override fun publicUrl(asset: ModelAsset, storageLocation: ModelStorageLocation): URI {
        requireCompatibleBucket(storageLocation, asset)
        val objectKey = ObjectKeyResolver(storageLocation.prefix)
            .resolve(asset.path)
        return urls.resolve(objectKey)
    }

    private fun requireCompatibleBucket(location: ModelStorageLocation, asset: ModelAsset) {
        if (location.bucket != configuredBucket) {
            throw ModelStorageException(StorageFailure.CONFIGURATION_MISMATCH, asset.path,
                IllegalStateException("Release bucket ${location.bucket} differs from configured bucket $configuredBucket"))
        }
    }
}
