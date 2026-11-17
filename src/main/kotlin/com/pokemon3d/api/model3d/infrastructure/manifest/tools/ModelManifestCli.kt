package com.pokemon3d.api.model3d.infrastructure.manifest.tools

import com.pokemon3d.api.model3d.infrastructure.manifest.ManifestJson
import java.nio.file.Path
import kotlin.system.exitProcess

internal object ModelManifestCli {
    fun run(args: Array<String>): String {
        require(args.isNotEmpty() && args[0] in setOf("extract", "verify")) { "Expected extract or verify" }
        val options = mutableMapOf<String, String>()
        val allowed = setOf("--data-root", "--catalog", "--manifest", "--dataset-version", "--expected-count")
        require((args.size - 1) % 2 == 0) { "Options must be --name value pairs" }
        args.drop(1).chunked(2).forEach { (key, value) ->
            require(key in allowed && key !in options && value.isNotBlank()) { "Invalid or duplicate option: $key" }
            options[key] = value
        }
        fun option(key: String): String = requireNotNull(options[key]) { "Missing $key" }
        val root = Path.of(option("--data-root"))
        val catalog = Path.of(option("--catalog"))
        val manifest = Path.of(option("--manifest"))
        val count = options["--expected-count"]?.let {
            requireNotNull(it.toIntOrNull()?.takeIf { count -> count > 0 }) { "--expected-count must be a positive JVM Int" }
        }
        val extractor = GlbMetadataExtractor()
        val result = if (args[0] == "extract") {
            require(manifest.toAbsolutePath().normalize() != catalog.toAbsolutePath().normalize()
                && manifest.toString().endsWith(".json")) { "Output must be a separate JSON file" }
            extractor.extract(root, catalog, option("--dataset-version"), count).also {
                ManifestWriter.writeAtomic(manifest, it)
            }
        } else {
            val actual = ManifestJson.read(manifest)
            val expected = extractor.extract(root, catalog, actual.datasetVersion, count)
            require(actual == expected) { "$manifest: manifest differs from current catalog/GLBs; re-extract to a new file and compare" }
            actual
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
