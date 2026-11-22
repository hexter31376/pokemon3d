package com.pokemon3d.api.publishing.application.service

import com.pokemon3d.api.assets.domain.ContentDigest
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.publishing.application.model.ImportManifest
import com.pokemon3d.api.publishing.application.port.input.ImportModels
import com.pokemon3d.api.publishing.application.port.output.ImportManifestReader
import com.pokemon3d.api.publishing.application.port.output.ModelReleaseRepository
import com.pokemon3d.api.publishing.application.port.output.ModelUploadSpool
import com.pokemon3d.api.publishing.domain.PreparedModel
import com.pokemon3d.api.publishing.domain.UploadRejectedException
import java.nio.file.Path
import java.util.UUID

class ModelImportService(
    private val releases: ModelReleaseRepository,
    private val manifests: ImportManifestReader,
    private val spool: ModelUploadSpool,
    private val storageLocation: ModelStorageLocation,
) : ImportModels {
    override fun submit(root: Path, manifestPath: Path, requestKey: String): UUID {
        val input = readManifest(manifestPath, "")
        releases.findRequest(requestKey, input.fingerprint)?.let { return it }
        val id = UUID.randomUUID()
        return releases.register(id, requestKey, input.datasetVersion, input.fingerprint,
            storageLocation, prepare(id, root, input))
    }

    override fun patch(root: Path, manifestPath: Path, requestKey: String): UUID {
        val input = readManifest(manifestPath, "patch\n")
        releases.findRequest(requestKey, input.fingerprint)?.let { return it }
        val base = releases.activeReleaseId() ?: throw UploadRejectedException("A patch requires an active release")
        val id = UUID.randomUUID()
        return releases.registerPatch(id, requestKey, input.datasetVersion, input.fingerprint,
            storageLocation, base, prepare(id, root, input))
    }

    private fun readManifest(path: Path, fingerprintPrefix: String): ImportManifest =
        manifests.read(path, fingerprintPrefix).also { input ->
            require(input.models.all { it.modelId.length <= 200 }) { "Model ID exceeds database limit" }
        }

    private fun prepare(id: UUID, root: Path, input: ImportManifest): List<PreparedModel> {
        val stagedAssets = mutableMapOf<String, Path>()
        return input.models.map { model ->
            val published = releases.findPublishedAsset(model.asset, storageLocation)
            if (published != null) {
                PreparedModel(model.copy(asset = published), spool.pathFor(id, model.asset))
            } else {
                val staged = stagedAssets.getOrPut(model.asset.sha256) { spool.stage(id, root, model) }
                val expected = ContentDigest(model.asset.sizeBytes, model.asset.sha256)
                require(spool.contentDigest(staged) == expected) { "GLB does not match metadata: ${model.modelId}" }
                val path = "objects/sha256/${model.asset.sha256}.glb"
                PreparedModel(model.copy(asset = model.asset.copy(path = path)), staged)
            }
        }
    }
}
