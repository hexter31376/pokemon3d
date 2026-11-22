package com.pokemon3d.api.publishing.adapter.output.manifest

import com.pokemon3d.api.publishing.adapter.output.manifest.tools.ManifestWriter
import com.pokemon3d.api.publishing.application.model.ModelManifest
import com.pokemon3d.api.publishing.application.port.output.ModelManifestStorage
import java.nio.file.Path

class JsonModelManifestStorage : ModelManifestStorage {
    override fun read(path: Path): ModelManifest = ManifestJson.read(path)
    override fun write(path: Path, manifest: ModelManifest) = ManifestWriter.writeAtomic(path, manifest)
}
