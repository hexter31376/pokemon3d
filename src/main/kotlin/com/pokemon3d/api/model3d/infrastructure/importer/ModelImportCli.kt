package com.pokemon3d.api.model3d.infrastructure.importer

import com.pokemon3d.api.Pokemon3dApplication
import com.pokemon3d.api.model3d.domain.ModelUploadRepository
import com.pokemon3d.api.model3d.domain.UploadRejectedException
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import java.nio.file.Path
import java.util.UUID
import kotlin.system.exitProcess

/** Administrative local CLI, deliberately not exposed as an unauthenticated HTTP endpoint. */
fun main(args: Array<String>) {
    try {
        require(args.isNotEmpty()) { "Expected submit|status|activate|retry" }
        val command = args[0]
        require(command in setOf("submit", "status", "activate", "retry")) { "Unknown command" }
        require((command == "submit" && args.size == 4) || (command != "submit" && args.size == 2)) {
            "submit <data-root> <manifest> <request-key> | status|activate|retry <release-id>"
        }
        SpringApplicationBuilder(Pokemon3dApplication::class.java).web(WebApplicationType.NONE).run(
            "--spring.docker.compose.enabled=false", "--pokemon.uploads.enabled=false", "--pokemon.storage.s3.enabled=false"
        ).use { context ->
            val jobs = context.getBean(ModelUploadRepository::class.java)
            val id = if (command == "submit") {
                val spool = Path.of(context.environment.getProperty("pokemon.uploads.spool-path", "runtime-data/uploads"))
                ManifestDatabaseImporter(jobs, spool).submit(Path.of(args[1]), Path.of(args[2]), args[3])
            } else UUID.fromString(args[1])
            when (command) {
                "activate" -> jobs.activate(id)
                "retry" -> jobs.retry(id)
            }
            println(jobs.status(id))
        }
    } catch (ex: Exception) {
        val detail = if (ex is UploadRejectedException || ex is IllegalArgumentException) ex.message else ex.javaClass.simpleName
        System.err.println("model import failed: $detail")
        exitProcess(1)
    }
}
