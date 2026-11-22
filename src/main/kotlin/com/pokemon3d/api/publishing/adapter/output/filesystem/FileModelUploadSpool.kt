package com.pokemon3d.api.publishing.adapter.output.filesystem

import com.pokemon3d.api.assets.domain.ContentDigest
import com.pokemon3d.api.assets.domain.ModelAsset
import com.pokemon3d.api.assets.domain.digest
import com.pokemon3d.api.catalog.domain.ModelMetadata
import com.pokemon3d.api.publishing.application.port.output.ModelUploadSpool
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class FileModelUploadSpool(private val spool: Path) : ModelUploadSpool {
    override fun pathFor(releaseId: UUID, asset: ModelAsset): Path =
        spool.toAbsolutePath().normalize().resolve(releaseId.toString()).resolve("${asset.sha256}.glb")

    override fun stage(releaseId: UUID, root: Path, model: ModelMetadata): Path {
        val dataRoot = root.toRealPath()
        val source = dataRoot.resolve(model.asset.path).toRealPath()
        require(source.startsWith(dataRoot)) { "Source escapes data root" }
        val staged = pathFor(releaseId, model.asset)
        Files.createDirectories(staged.parent)
        // A lost DB acknowledgement can still mean the release committed; retain staged bytes.
        Files.copy(source, staged)
        return staged
    }

    override fun contentDigest(source: Path): ContentDigest = Files.newInputStream(source).use(::digest)

    override fun remove(sources: List<Path>): Int = CompletedUploadSpool(spool).remove(sources)
}
