package com.pokemon3d.api.assets.application.port.output

import com.pokemon3d.api.assets.domain.ModelAsset
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.assets.domain.StoredModelAsset
import java.net.URI

/** Read-only file access; no dependency on S3 or HTTP response types. */
interface ModelAssetStorage {
    /** Null means the object is absent in an accessible bucket, never a connection failure. */
    fun find(asset: ModelAsset, storageLocation: ModelStorageLocation): StoredModelAsset?
    fun publicUrl(asset: ModelAsset, storageLocation: ModelStorageLocation): URI
}
