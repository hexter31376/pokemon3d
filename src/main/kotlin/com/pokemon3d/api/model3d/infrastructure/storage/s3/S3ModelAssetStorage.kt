package com.pokemon3d.api.model3d.infrastructure.storage.s3

import com.pokemon3d.api.model3d.domain.ModelAsset
import com.pokemon3d.api.model3d.domain.ModelAssetStorage
import com.pokemon3d.api.model3d.domain.StoredModelAsset
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

class S3ModelAssetStorage(client: S3Client, properties: S3StorageProperties) : ModelAssetStorage {
    private val objects = S3ObjectStore(client, properties.bucket)
    private val keys = ObjectKeyResolver(properties.prefix)
    private val urls = PublicAssetUrlResolver(properties.publicBaseUrl)
    override fun find(asset: ModelAsset): StoredModelAsset? = objects.head(keys.resolve(asset.path))
    override fun publicUrl(asset: ModelAsset): URI = urls.resolve(keys.resolve(asset.path))
}
