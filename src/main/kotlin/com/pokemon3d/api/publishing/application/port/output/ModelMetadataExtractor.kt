package com.pokemon3d.api.publishing.application.port.output

import com.pokemon3d.api.publishing.application.model.ModelManifest
import java.nio.file.Path

interface ModelMetadataExtractor {
    fun extract(root: Path, catalog: Path, datasetVersion: String, expectedCount: Int? = null): ModelManifest
}
