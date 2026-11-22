package com.pokemon3d.api.assets.application.service

import com.pokemon3d.api.assets.application.port.input.FindModelAsset
import com.pokemon3d.api.assets.application.port.output.ModelAssetStorage
import com.pokemon3d.api.assets.domain.ModelAsset
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.assets.domain.exception.ModelStorageException
import com.pokemon3d.api.assets.domain.exception.StorageFailure
import java.net.URI

class ModelAssetQueryService(private val storage: ModelAssetStorage?) : FindModelAsset {
    override fun findUrl(asset: ModelAsset, location: ModelStorageLocation): URI? {
        val availableStorage = storage ?: throw ModelStorageException(StorageFailure.UNAVAILABLE, asset.path)
        val stored = availableStorage.find(asset, location) ?: return null
        if (!stored.matches(asset)) throw ModelStorageException(StorageFailure.INTEGRITY_MISMATCH, asset.path)
        return availableStorage.publicUrl(asset, location)
    }
}
