package com.pokemon3d.api.assets.application.port.input

import com.pokemon3d.api.assets.domain.ModelAsset
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import java.nio.file.Path

/** Returns only after remote bytes and metadata have been verified. */
fun interface PublishModelAsset {
    fun publish(asset: ModelAsset, source: Path, storageLocation: ModelStorageLocation)
}
