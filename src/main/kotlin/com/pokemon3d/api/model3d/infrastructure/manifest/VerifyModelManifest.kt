package com.pokemon3d.api.model3d.infrastructure.manifest

import java.nio.file.Path

/** Operational verification with the same loader as Spring, without starting a server or DB. */
fun main(args: Array<String>) {
    require(args.size == 1 && args[0].isNotBlank()) { "Usage: verifyModelManifest -PmanifestPath=/absolute/path/manifest.json" }
    val repository = JsonManifestModelMetadataRepository(Path.of(args[0]))
    println("Manifest OK: dataset=${repository.datasetVersion}, models=${repository.findAll().size}, " +
        "motions=${repository.findAll().sumOf { it.motions.size }}")
}
