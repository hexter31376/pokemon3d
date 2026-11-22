package com.pokemon3d.api.publishing.application.port.output

import com.pokemon3d.api.publishing.application.model.ModelManifest
import java.nio.file.Path

interface ModelManifestStorage {
    fun read(path: Path): ModelManifest
    fun write(path: Path, manifest: ModelManifest)
}
