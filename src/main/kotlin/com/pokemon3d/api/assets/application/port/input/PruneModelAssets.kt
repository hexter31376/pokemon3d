package com.pokemon3d.api.assets.application.port.input

import com.pokemon3d.api.assets.domain.ModelAssetRetention
import com.pokemon3d.api.assets.domain.PrunedModelAssets

fun interface PruneModelAssets {
    fun prune(retention: ModelAssetRetention): PrunedModelAssets
}
