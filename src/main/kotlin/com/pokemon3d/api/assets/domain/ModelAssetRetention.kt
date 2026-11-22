package com.pokemon3d.api.assets.domain

data class ModelAssetRetention(
    val protectedPaths: Set<String>,
    val obsoletePaths: Set<String>,
    val retiredReleaseIds: Set<String>,
)
