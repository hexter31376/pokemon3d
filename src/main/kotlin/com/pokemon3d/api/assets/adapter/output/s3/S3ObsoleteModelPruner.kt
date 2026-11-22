package com.pokemon3d.api.assets.adapter.output.s3

import com.pokemon3d.api.assets.application.port.output.ModelAssetDeletion
import com.pokemon3d.api.assets.domain.ModelAssetRetention
import com.pokemon3d.api.assets.domain.PrunedModelAssets
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.Delete
import software.amazon.awssdk.services.s3.model.ObjectIdentifier

/** Deletes only managed GLBs; manifests, unrelated objects and protected paths are untouched. */
class S3ObsoleteModelPruner(private val client: S3Client, private val properties: S3StorageProperties) : ModelAssetDeletion {
    override fun deleteObsolete(retention: ModelAssetRetention): PrunedModelAssets {
        require(retention.protectedPaths.isNotEmpty()) { "An explicit nonempty retention set is required" }
        val resolver = ObjectKeyResolver(properties.prefix)
        val retainedKeys = retention.protectedPaths.map { resolver.resolve(it) }.toSet()
        val prefix = properties.prefix.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        val obsolete = client.listObjectsV2Paginator { it.bucket(properties.bucket).prefix(prefix) }
            .contents().filter { obj ->
                val path = obj.key().removePrefix(prefix)
                retention.canDelete(path) && obj.key() !in retainedKeys
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

    private companion object {
        const val DELETE_BATCH_SIZE = 1000
    }
}
