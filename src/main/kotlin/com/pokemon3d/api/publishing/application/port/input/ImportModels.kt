package com.pokemon3d.api.publishing.application.port.input

import java.nio.file.Path
import java.util.UUID

interface ImportModels {
    fun submit(root: Path, manifestPath: Path, requestKey: String): UUID
    fun patch(root: Path, manifestPath: Path, requestKey: String): UUID
}
