package com.pokemon3d.api.model3d.infrastructure.importer

import com.pokemon3d.api.model3d.domain.*
import com.pokemon3d.api.model3d.infrastructure.manifest.ManifestJson
import com.pokemon3d.api.model3d.infrastructure.manifest.ManifestValidator
import com.pokemon3d.api.model3d.infrastructure.manifest.ModelManifest
import com.pokemon3d.api.model3d.infrastructure.storage.s3.ContentDigest
import com.pokemon3d.api.model3d.infrastructure.storage.s3.digest
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** One-time import boundary. The running application never opens a manifest. */
class ManifestDatabaseImporter(
    private val jobs: ModelUploadRepository,
    private val spool: Path,
    private val storageLocation: ModelStorageLocation,
) {
    fun submit(root: Path, manifestPath: Path, requestKey: String): UUID {
        val input = readManifest(manifestPath)
        jobs.findRequest(requestKey, input.fingerprint)?.let { return it }
        val id = UUID.randomUUID()
        return jobs.register(id, requestKey, input.manifest.datasetVersion, input.fingerprint,
            storageLocation, prepare(id, root, input.manifest))
    }

    /** The patch manifest contains only changed or added models; omitted models keep their references. */
    fun patch(root: Path, manifestPath: Path, requestKey: String): UUID {
        val input = readManifest(manifestPath, "patch\n")
        jobs.findRequest(requestKey, input.fingerprint)?.let { return it }
        val base = jobs.activeReleaseId() ?: throw UploadRejectedException("A patch requires an active release")
        val id = UUID.randomUUID()
        return jobs.registerPatch(id, requestKey, input.manifest.datasetVersion, input.fingerprint,
            storageLocation, base, prepare(id, root, input.manifest))
    }

    private fun readManifest(path: Path, fingerprintPrefix: String = ""): ImportManifest {
        val bytes = Files.readAllBytes(path)
        val manifest = ManifestJson.mapper.readValue(bytes, ModelManifest::class.java)
        ManifestValidator.validate(manifest)
        require(manifest.models.all { it.modelId.length <= 200 }) { "Model ID exceeds database limit" }
        val fingerprint = (fingerprintPrefix.toByteArray() + bytes).inputStream().use(::digest).sha256
        return ImportManifest(manifest, fingerprint)
    }

    private fun prepare(id: UUID, root: Path, manifest: ModelManifest): List<PreparedModel> {
        val directory = spool.toAbsolutePath().normalize().resolve(id.toString())
        val stagedAssets = mutableMapOf<String, Path>()
        return manifest.models.map { model ->
            val published = jobs.findPublishedAsset(model.asset, storageLocation)
            if (published != null) {
                PreparedModel(model.copy(asset = published), directory.resolve("${model.asset.sha256}.glb"))
            } else {
                val staged = stagedAssets.getOrPut(model.asset.sha256) { stage(root, directory, model) }
                val expected = ContentDigest(model.asset.sizeBytes, model.asset.sha256)
                require(Files.newInputStream(staged).use(::digest) == expected) { "GLB does not match metadata: ${model.modelId}" }
                val path = "objects/sha256/${model.asset.sha256}.glb"
                PreparedModel(model.copy(asset = model.asset.copy(path = path)), staged)
            }
        }
    }

    private fun stage(root: Path, directory: Path, model: ModelMetadata): Path {
        val dataRoot = root.toRealPath()
        val source = dataRoot.resolve(model.asset.path).toRealPath()
        require(source.startsWith(dataRoot)) { "Source escapes data root" }
        Files.createDirectories(directory)
        val staged = directory.resolve("${model.asset.sha256}.glb")
        // Retain staged bytes if DB commit acknowledgement is lost; registration may have committed.
        Files.copy(source, staged)
        return staged
    }

    private data class ImportManifest(val manifest: ModelManifest, val fingerprint: String)
}
