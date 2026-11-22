package com.pokemon3d.api.publishing.application.port.input

import com.pokemon3d.api.publishing.application.model.ModelPruningResult

fun interface PrunePublishedModels {
    fun prune(): ModelPruningResult
}
