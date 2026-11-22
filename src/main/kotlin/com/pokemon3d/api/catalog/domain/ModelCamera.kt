package com.pokemon3d.api.catalog.domain

data class ModelCamera(
    val cameraName: String,
    val direction: List<Double>,
    val fov: Double,
    val padding: Double,
    val target: List<Double>,
    val bounds: ModelBounds,
)
