package com.pokemon3d.api.model3d.domain

data class ModelAsset(
    val path: String,
    val sizeBytes: Long,
    val sha256: String,
    val requiredExtensions: List<String>,
)