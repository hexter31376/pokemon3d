package com.pokemon3d.api.model3d.infrastructure.storage.s3

import com.pokemon3d.api.model3d.domain.ModelAssetRetention
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.Delete
import software.amazon.awssdk.services.s3.model.ObjectIdentifier

/** Deletes only managed GLBs; manifests, unrelated objects and protected paths are untouched. */
class S3ObsoleteModelPruner(private val client: S3Client, private val properties: S3StorageProperties) {
    fun prune(retention: ModelAssetRetention): PrunedModelAssets {
        require(retention.protectedPaths.isNotEmpty()) { "An explicit nonempty retention set is required" }
        val resolver = ObjectKeyResolver(properties.prefix)
        val retainedKeys = retention.protectedPaths.map { resolver.resolve(it) }.toSet()
        val prefix = properties.prefix.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        val obsolete = client.listObjectsV2Paginator { it.bucket(properties.bucket).prefix(prefix) }
            .contents().filter { obj ->
                val path = obj.key().removePrefix(prefix)
                isObsoleteGlb(path, retention) && obj.key() !in retainedKeys
            }.toList()
        obsolete.chunked(DELETE_BATCH_SIZE).forEach { batch ->
            val result = client.deleteObjects { request ->
                request.bucket(properties.bucket).delete(Delete.builder().objects(batch.map {
                    ObjectIdentifier.builder().key(it.key()).build()
                }).quiet(true).build())
            }
            check(result.errors().isEmpty()) { "S3 did not delete ${result.errors().size} obsolete GLBs" }
        }
        return PrunedModelAssets(obsolete.size, obsolete.sumOf { it.size() })
    }

    private fun isObsoleteGlb(path: String, retention: ModelAssetRetention): Boolean {
        if (!path.endsWith(".glb")) return false
        if (path in retention.obsoletePaths) return true
        val parts = path.split('/')
        return parts.size >= 3 && parts[0] == "releases" && parts[1] in retention.retiredReleaseIds
    }

    private companion object {
        const val DELETE_BATCH_SIZE = 1000
    }
}

data class PrunedModelAssets(val deletedObjects: Int, val logicalBytes: Long)
