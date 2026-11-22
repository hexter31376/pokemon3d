package com.pokemon3d.api.assets.domain

import java.net.URI

/** Read-only file access; no dependency on S3 or HTTP response types. */
interface ModelAssetStorage {
    /** Null means the object is absent in an accessible bucket, never a connection failure. */
    fun find(asset: ModelAsset, storageLocation: ModelStorageLocation): StoredModelAsset?
    fun publicUrl(asset: ModelAsset, storageLocation: ModelStorageLocation): URI
}

data class StoredModelAsset(val sizeBytes: Long, val contentType: String?, val sha256: String?) {
    fun matches(asset: ModelAsset): Boolean =
        sizeBytes == asset.sizeBytes && contentType == "model/gltf-binary" && sha256 == asset.sha256
}
