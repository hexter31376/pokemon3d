package com.pokemon3d.api.assets.configuration

import com.pokemon3d.api.assets.adapter.output.s3.S3ClientFactory
import com.pokemon3d.api.assets.adapter.output.s3.S3ObsoleteModelPruner
import com.pokemon3d.api.assets.adapter.output.s3.S3StorageProperties
import com.pokemon3d.api.assets.application.port.input.PruneModelAssets
import com.pokemon3d.api.assets.application.service.ModelAssetPruningService
import com.pokemon3d.api.assets.domain.ModelStorageLocation

/** CLI composition is lazy so an S3 connection is opened only after maintenance is reserved. */
class AssetMaintenanceConfiguration(readProperty: (String) -> String?) {
    private val properties = S3StorageProperties.fromProperties(readProperty)
    val location = ModelStorageLocation(properties.bucket, properties.prefix)

    fun pruning(): PruneModelAssets = ModelAssetPruningService { retained ->
        S3ClientFactory.create(properties).use { client ->
            S3ObsoleteModelPruner(client, properties).deleteObsolete(retained)
        }
    }
}
