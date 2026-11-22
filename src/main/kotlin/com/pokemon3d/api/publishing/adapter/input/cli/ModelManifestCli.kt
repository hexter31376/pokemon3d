package com.pokemon3d.api.publishing.adapter.input.cli

import com.pokemon3d.api.publishing.configuration.ManifestToolConfiguration
import java.nio.file.Path
import kotlin.system.exitProcess

internal object ModelManifestCli {
    fun run(args: Array<String>): String {
        require(args.isNotEmpty() && args[0] in setOf("extract", "verify")) { "Expected extract or verify" }
        val options = mutableMapOf<String, String>()
        val allowed = setOf("--data-root", "--catalog", "--manifest", "--dataset-version", "--expected-count")
        require((args.size - 1) % 2 == 0) { "Options must be --name value pairs" }
        args.drop(1)
            .chunked(2)
            .forEach { (key, value) ->
                require(key in allowed && key !in options && value.isNotBlank()) {
                    "Invalid or duplicate option: $key"
                }
                options[key] = value
            }
        fun option(key: String): String = requireNotNull(options[key]) { "Missing $key" }
        val root = Path.of(option("--data-root"))
        val catalog = Path.of(option("--catalog"))
        val manifest = Path.of(option("--manifest"))
        val count = options["--expected-count"]?.let {
            requireNotNull(it.toIntOrNull()?.takeIf { count -> count > 0 }) { "--expected-count must be a positive JVM Int" }
        }
        val manifests = ManifestToolConfiguration.manifests()
        val result = if (args[0] == "extract") {
            manifests.extract(root, catalog, manifest, option("--dataset-version"), count)
        } else {
            manifests.verify(root, catalog, manifest, count)
        }
        return "${args[0]}: OK, ${result.modelCount} models, ${result.models.sumOf { it.motions.size }} motions, schema=${result.schemaVersion}"
    }
}

fun main(args: Array<String>) {
    try { println(ModelManifestCli.run(args)) } catch (ex: Exception) {
        System.err.println("manifest error: ${ex.message}")
        exitProcess(1)
    }
}
