package com.pokemon3d.api.catalog.application.port.input

import com.pokemon3d.api.catalog.application.model.ModelResource

fun interface FindModelResource {
    fun findById(modelId: String): ModelResource
}
