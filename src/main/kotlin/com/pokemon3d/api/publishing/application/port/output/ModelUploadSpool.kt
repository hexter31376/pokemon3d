package com.pokemon3d.api.publishing.application.port.output

import com.pokemon3d.api.assets.domain.ContentDigest
import com.pokemon3d.api.assets.domain.ModelAsset
import com.pokemon3d.api.catalog.domain.ModelMetadata
import java.nio.file.Path
import java.util.UUID

interface ModelUploadSpool {
    fun pathFor(releaseId: UUID, asset: ModelAsset): Path
    fun stage(releaseId: UUID, root: Path, model: ModelMetadata): Path
    fun contentDigest(source: Path): ContentDigest
    fun remove(sources: List<Path>): Int
}
