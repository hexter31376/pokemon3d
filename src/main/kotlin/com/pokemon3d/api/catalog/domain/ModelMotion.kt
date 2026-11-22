package com.pokemon3d.api.catalog.domain

data class ModelMotion(
    val index: Int,
    val name: String,
    val role: String,
    val startTimeSeconds: Double,
    val endTimeSeconds: Double,
    val durationSeconds: Double,
)
