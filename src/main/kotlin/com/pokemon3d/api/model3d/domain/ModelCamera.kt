package com.pokemon3d.api.model3d.domain

data class ModelCamera(
    val cameraName: String,
    val direction: List<Double>,
    val fov: Double,
    val padding: Double,
    val target: List<Double>,
    val bounds: ModelBounds,
)
