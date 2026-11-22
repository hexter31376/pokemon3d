package com.pokemon3d.api.assets.application.service

import com.pokemon3d.api.assets.application.port.input.PruneModelAssets
import com.pokemon3d.api.assets.application.port.output.ModelAssetDeletion
import com.pokemon3d.api.assets.domain.ModelAssetRetention
import com.pokemon3d.api.assets.domain.PrunedModelAssets

class ModelAssetPruningService(private val deletion: ModelAssetDeletion) : PruneModelAssets {
    override fun prune(retention: ModelAssetRetention): PrunedModelAssets {
        require(retention.protectedPaths.isNotEmpty()) { "An explicit nonempty retention set is required" }
        return deletion.deleteObsolete(retention)
    }
}
