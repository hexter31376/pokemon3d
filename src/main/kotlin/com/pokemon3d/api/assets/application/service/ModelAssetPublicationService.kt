package com.pokemon3d.api.assets.application.service

import com.pokemon3d.api.assets.application.port.input.PublishModelAsset
import com.pokemon3d.api.assets.application.port.output.AssetSourceReader
import com.pokemon3d.api.assets.application.port.output.ModelAssetUploadStore
import com.pokemon3d.api.assets.domain.ContentDigest
import com.pokemon3d.api.assets.domain.ModelAsset
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.assets.domain.exception.ModelStorageException
import com.pokemon3d.api.assets.domain.exception.StorageFailure
import java.nio.file.Path

class ModelAssetPublicationService(
    private val storage: ModelAssetUploadStore,
    private val sources: AssetSourceReader,
) : PublishModelAsset {
    override fun publish(asset: ModelAsset, source: Path, storageLocation: ModelStorageLocation) {
        val expected = ContentDigest(asset.sizeBytes, asset.sha256)
        // A retry after a successful upload must work even if the local spool has been removed.
        if (storage.find(asset, storageLocation) == null) {
            require(sources.contentDigest(source) == expected) { "Staged GLB differs from expected bytes" }
            storage.uploadIfAbsent(asset, source, storageLocation)
        }
        if (storage.find(asset, storageLocation)?.matches(asset) != true ||
            storage.contentDigest(asset, storageLocation) != expected
        ) {
            throw ModelStorageException(StorageFailure.INTEGRITY_MISMATCH, asset.path)
        }
    }
}
