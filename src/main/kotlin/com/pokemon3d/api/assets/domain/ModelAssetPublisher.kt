package com.pokemon3d.api.assets.domain

import java.nio.file.Path

/** Returns only after remote bytes and metadata have been verified. */
fun interface ModelAssetPublisher {
    fun publish(asset: ModelAsset, source: Path, storageLocation: ModelStorageLocation)
}
