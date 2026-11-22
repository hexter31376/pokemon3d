package com.pokemon3d.api.publishing.application.port.input

import com.pokemon3d.api.publishing.application.model.ModelManifest
import java.nio.file.Path

interface ManageModelManifest {
    fun extract(root: Path, catalog: Path, manifest: Path, datasetVersion: String, expectedCount: Int?): ModelManifest
    fun verify(root: Path, catalog: Path, manifest: Path, expectedCount: Int?): ModelManifest
    fun inspect(manifest: Path): ModelManifest
}
