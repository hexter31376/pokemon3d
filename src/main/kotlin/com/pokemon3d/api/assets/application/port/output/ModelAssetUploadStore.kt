package com.pokemon3d.api.assets.application.port.output

import com.pokemon3d.api.assets.domain.ContentDigest
import com.pokemon3d.api.assets.domain.ModelAsset
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.assets.domain.StoredModelAsset
import java.nio.file.Path

interface ModelAssetUploadStore {
    fun find(asset: ModelAsset, location: ModelStorageLocation): StoredModelAsset?
    /** Must not overwrite an object created by a concurrent publisher. */
    fun uploadIfAbsent(asset: ModelAsset, source: Path, location: ModelStorageLocation)
    fun contentDigest(asset: ModelAsset, location: ModelStorageLocation): ContentDigest
}
