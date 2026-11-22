package com.pokemon3d.api.assets.domain

data class ModelAssetRetention(
    val protectedPaths: Set<String>,
    val obsoletePaths: Set<String>,
    val retiredReleaseIds: Set<String>,
) {
    fun canDelete(path: String): Boolean {
        if (!path.endsWith(".glb") || path in protectedPaths) return false
        if (path in obsoletePaths) return true
        val parts = path.split('/')
        return parts.size >= 3 && parts[0] == "releases" && parts[1] in retiredReleaseIds
    }
}
