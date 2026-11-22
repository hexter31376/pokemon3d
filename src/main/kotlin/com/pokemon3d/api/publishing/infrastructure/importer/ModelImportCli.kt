package com.pokemon3d.api.publishing.infrastructure.importer

import com.pokemon3d.api.Pokemon3dApplication
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.assets.infrastructure.storage.s3.S3ClientFactory
import com.pokemon3d.api.assets.infrastructure.storage.s3.S3ObsoleteModelPruner
import com.pokemon3d.api.assets.infrastructure.storage.s3.S3StorageProperties
import com.pokemon3d.api.publishing.domain.ModelReleaseRepository
import com.pokemon3d.api.publishing.domain.ModelUploadQueue
import com.pokemon3d.api.publishing.domain.UploadRejectedException
import com.pokemon3d.api.publishing.infrastructure.persistence.ModelAssetRetentionRepository
import java.nio.file.Path
import java.util.UUID
import kotlin.system.exitProcess
import org.springframework.beans.factory.getBean
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder

/** Administrative local CLI, deliberately not exposed as an unauthenticated HTTP endpoint. */
fun main(args: Array<String>) {
    try {
        require(args.isNotEmpty()) { "Expected submit|patch|status|activate|retry|prune" }
        val command = args[0]
        require(command in setOf("submit", "patch", "status", "activate", "retry", "prune")) { "Unknown command" }
        val importsModels = command in setOf("submit", "patch")
        require((importsModels && args.size == 4) || (command == "prune" && args.size == 1) || (!importsModels && command != "prune" && args.size == 2)) {
            "submit|patch <data-root> <manifest> <request-key> | status|activate|retry <release-id> | prune"
        }
        SpringApplicationBuilder(Pokemon3dApplication::class.java)
            .web(WebApplicationType.NONE)
            .run(
                "--spring.profiles.active=local",
                "--spring.docker.compose.enabled=false",
                "--pokemon.uploads.enabled=false",
                "--pokemon.storage.s3.enabled=false",
            )
            .use { context ->
                val releases = context.getBean<ModelReleaseRepository>()
                if (command == "prune") {
                    val properties = S3StorageProperties.fromProperties { context.environment.getProperty(it) }
                    val retention = context.getBean<ModelAssetRetentionRepository>()
                    val retained = retention.retainCurrentAssets(
                        ModelStorageLocation(properties.bucket, properties.prefix),
                    )
                    try {
                        S3ClientFactory.create(properties).use { client ->
                            println(S3ObsoleteModelPruner(client, properties).prune(retained))
                        }
                        val spool = Path.of(context.environment.getProperty("pokemon.uploads.spool-path", "runtime-data/uploads"))
                        println("Completed spool files removed: ${CompletedUploadSpool(spool).remove(context.getBean<ModelUploadQueue>().completedSourcePaths())}")
                    } finally {
                        retention.finishPruning()
                    }
                    return@use
                }
                val id = if (importsModels) {
                    val spool = Path.of(
                        context.environment.getProperty(
                            "pokemon.uploads.spool-path",
                            "runtime-data/uploads",
                        ),
                    )
                    val location = ModelStorageLocation(
                        context.environment.getRequiredProperty("pokemon.storage.s3.bucket"),
                        context.environment.getProperty("pokemon.storage.s3.prefix", ""),
                    )
                    val importer = ManifestDatabaseImporter(releases, spool, location)
                    if (command == "patch") importer.patch(Path.of(args[1]), Path.of(args[2]), args[3])
                    else importer.submit(Path.of(args[1]), Path.of(args[2]), args[3])
                } else UUID.fromString(args[1])
                when (command) {
                    "activate" -> releases.activate(id)
                    "retry" -> releases.retry(id)
                }
                println(releases.status(id))
            }
    } catch (ex: Exception) {
        val detail = if (ex is UploadRejectedException || ex is IllegalArgumentException) ex.message else ex.javaClass.simpleName
        System.err.println("model import failed: $detail")
        exitProcess(1)
    }
}
