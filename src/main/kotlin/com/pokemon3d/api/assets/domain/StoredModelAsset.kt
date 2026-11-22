package com.pokemon3d.api.assets.domain

data class StoredModelAsset(val sizeBytes: Long, val contentType: String?, val sha256: String?) {
    fun matches(asset: ModelAsset): Boolean =
        sizeBytes == asset.sizeBytes && contentType == "model/gltf-binary" && sha256 == asset.sha256
}
