package com.pokemon3d.api.assets.application.port.output

import com.pokemon3d.api.assets.domain.ModelAssetRetention
import com.pokemon3d.api.assets.domain.PrunedModelAssets

fun interface ModelAssetDeletion {
    fun deleteObsolete(retention: ModelAssetRetention): PrunedModelAssets
}
