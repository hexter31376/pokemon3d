package com.pokemon3d.api.publishing.adapter.input.cli

import com.pokemon3d.api.publishing.configuration.ManifestToolConfiguration
import java.nio.file.Path

/** Operational verification with the same loader as Spring, without starting a server or DB. */
fun main(args: Array<String>) {
    require(args.size == 1 && args[0].isNotBlank()) {
        "Usage: verifyModelManifest -PmanifestPath=/absolute/path/manifest.json"
    }
    val manifest = ManifestToolConfiguration.manifests().inspect(Path.of(args[0]))
    val models = manifest.models
    println(
        "Manifest OK: dataset=${manifest.datasetVersion}, models=${models.size}, " +
            "motions=${models.sumOf { it.motions.size }}",
    )
}
