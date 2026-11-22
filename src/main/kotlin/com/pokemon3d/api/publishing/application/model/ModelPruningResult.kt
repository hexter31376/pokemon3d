package com.pokemon3d.api.publishing.application.model

import com.pokemon3d.api.assets.domain.PrunedModelAssets

data class ModelPruningResult(val assets: PrunedModelAssets, val removedSources: Int)
