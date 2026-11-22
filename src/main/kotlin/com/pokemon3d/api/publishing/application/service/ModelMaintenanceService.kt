package com.pokemon3d.api.publishing.application.service

import com.pokemon3d.api.assets.application.port.input.PruneModelAssets
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.publishing.application.model.ModelPruningResult
import com.pokemon3d.api.publishing.application.port.input.PrunePublishedModels
import com.pokemon3d.api.publishing.application.port.output.ModelAssetRetentionStore
import com.pokemon3d.api.publishing.application.port.output.ModelUploadQueue
import com.pokemon3d.api.publishing.application.port.output.ModelUploadSpool

class ModelMaintenanceService(
    private val retention: ModelAssetRetentionStore,
    private val assets: PruneModelAssets,
    private val uploads: ModelUploadQueue,
    private val spool: ModelUploadSpool,
    private val location: ModelStorageLocation,
) : PrunePublishedModels {
    override fun prune(): ModelPruningResult {
        // Reserve first: a rejected reservation must not release another process's maintenance lock.
        val retained = retention.retainCurrentAssets(location)
        try {
            val pruned = assets.prune(retained)
            val removedSources = spool.remove(uploads.completedSourcePaths())
            return ModelPruningResult(pruned, removedSources)
        } finally {
            retention.finishPruning()
        }
    }
}
