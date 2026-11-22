package com.pokemon3d.api.publishing.configuration

import com.pokemon3d.api.publishing.adapter.output.manifest.JsonModelManifestStorage
import com.pokemon3d.api.publishing.adapter.output.manifest.tools.GlbMetadataExtractor
import com.pokemon3d.api.publishing.application.port.input.ManageModelManifest
import com.pokemon3d.api.publishing.application.service.ModelManifestService

object ManifestToolConfiguration {
    fun manifests(): ManageModelManifest = ModelManifestService(JsonModelManifestStorage(), GlbMetadataExtractor())
}
