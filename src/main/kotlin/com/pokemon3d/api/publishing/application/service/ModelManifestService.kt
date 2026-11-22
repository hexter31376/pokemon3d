package com.pokemon3d.api.publishing.application.service

import com.pokemon3d.api.publishing.application.model.ModelManifest
import com.pokemon3d.api.publishing.application.port.input.ManageModelManifest
import com.pokemon3d.api.publishing.application.port.output.ModelManifestStorage
import com.pokemon3d.api.publishing.application.port.output.ModelMetadataExtractor
import java.nio.file.Path

class ModelManifestService(
    private val storage: ModelManifestStorage,
    private val extractor: ModelMetadataExtractor,
) : ManageModelManifest {
    override fun extract(root: Path, catalog: Path, manifest: Path, datasetVersion: String, expectedCount: Int?): ModelManifest {
        require(manifest.toAbsolutePath().normalize() != catalog.toAbsolutePath().normalize() &&
            manifest.toString().endsWith(".json")
        ) { "Output must be a separate JSON file" }
        return extractor.extract(root, catalog, datasetVersion, expectedCount).also { storage.write(manifest, it) }
    }

    override fun verify(root: Path, catalog: Path, manifest: Path, expectedCount: Int?): ModelManifest {
        val actual = storage.read(manifest)
        val expected = extractor.extract(root, catalog, actual.datasetVersion, expectedCount)
        require(actual == expected) { "$manifest: manifest differs from current catalog/GLBs; re-extract to a new file and compare" }
        return actual
    }

    override fun inspect(manifest: Path): ModelManifest = storage.read(manifest)
}
