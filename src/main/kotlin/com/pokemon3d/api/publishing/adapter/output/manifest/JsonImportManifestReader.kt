package com.pokemon3d.api.publishing.adapter.output.manifest

import com.pokemon3d.api.assets.domain.digest
import com.pokemon3d.api.publishing.application.model.ImportManifest
import com.pokemon3d.api.publishing.application.model.ModelManifest
import com.pokemon3d.api.publishing.application.port.output.ImportManifestReader
import java.nio.file.Files
import java.nio.file.Path

class JsonImportManifestReader : ImportManifestReader {
    override fun read(path: Path, fingerprintPrefix: String): ImportManifest {
        val bytes = Files.readAllBytes(path)
        val manifest = ManifestJson.mapper.readValue(bytes, ModelManifest::class.java)
        ManifestValidator.validate(manifest)
        val fingerprint = (fingerprintPrefix.toByteArray() + bytes).inputStream().use(::digest).sha256
        return ImportManifest(manifest.datasetVersion, manifest.models, fingerprint)
    }
}
