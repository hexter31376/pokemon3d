package com.pokemon3d.api.publishing.application.port.output

import com.pokemon3d.api.publishing.application.model.ImportManifest
import java.nio.file.Path

fun interface ImportManifestReader {
    fun read(path: Path, fingerprintPrefix: String): ImportManifest
}
