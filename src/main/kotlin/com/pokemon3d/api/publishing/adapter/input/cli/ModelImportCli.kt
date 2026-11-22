package com.pokemon3d.api.publishing.adapter.input.cli

import com.pokemon3d.api.publishing.configuration.PublishingCliConfiguration
import com.pokemon3d.api.publishing.domain.UploadRejectedException
import java.nio.file.Path
import java.util.UUID
import kotlin.system.exitProcess

/** Administrative local CLI, deliberately not exposed as an unauthenticated HTTP endpoint. */
fun main(args: Array<String>) {
    try {
        require(args.isNotEmpty()) { "Expected submit|patch|status|activate|retry|prune" }
        val command = args[0]
        val expectedArguments = when (command) {
            "submit", "patch" -> 4
            "prune" -> 1
            "status", "activate", "retry" -> 2
            else -> throw IllegalArgumentException("Unknown command")
        }
        require(args.size == expectedArguments) {
            "submit|patch <data-root> <manifest> <request-key> | status|activate|retry <release-id> | prune"
        }
        PublishingCliConfiguration.withServices { services ->
            if (command == "prune") {
                val result = services.maintenance().prune()
                println(result.assets)
                println("Completed spool files removed: ${result.removedSources}")
            } else {
                val releases = services.releases()
                val result = when (command) {
                    "submit" -> releases.status(services.imports().submit(Path.of(args[1]), Path.of(args[2]), args[3]))
                    "patch" -> releases.status(services.imports().patch(Path.of(args[1]), Path.of(args[2]), args[3]))
                    "activate" -> releases.activate(UUID.fromString(args[1]))
                    "retry" -> releases.retry(UUID.fromString(args[1]))
                    else -> releases.status(UUID.fromString(args[1]))
                }
                println(result)
            }
        }
    } catch (ex: Exception) {
        val detail = if (ex is UploadRejectedException || ex is IllegalArgumentException) ex.message else ex.javaClass.simpleName
        System.err.println("model import failed: $detail")
        exitProcess(1)
    }
}
