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
class ManifestDatabaseImporter(private val jobs: ModelUploadRepository, private val spool: Path) {
    fun submit(root: Path, manifestPath: Path, requestKey: String): UUID {
        val bytes = Files.readAllBytes(manifestPath)
        val manifest = ManifestJson.mapper.readValue(bytes, ModelManifest::class.java).also(ManifestValidator::validate)
        require(manifest.models.all { it.modelId.length <= 200 }) { "Model ID exceeds database limit" }
        val fingerprint = bytes.inputStream().use(::digest).sha256
        jobs.findRequest(requestKey, fingerprint)?.let { return it }
        val dataRoot = root.toRealPath()
        val id = UUID.randomUUID()
        val directory = Files.createDirectories(spool.toAbsolutePath().normalize().resolve(id.toString()))
        // On DB connection loss at commit, retain this directory: the registration may have committed.
        // Unreferenced directories can be garbage-collected only after checking the DB.
        val prepared = manifest.models.map { model ->
            val source = dataRoot.resolve(model.asset.path).toRealPath()
            require(source.startsWith(dataRoot)) { "Source escapes data root" }
            val staged = directory.resolve("${model.modelId}.glb")
            Files.newInputStream(source).use { input -> Files.newOutputStream(staged).use { output -> input.copyTo(output) } }
            val actual = Files.newInputStream(staged).use(::digest)
            require(actual == ContentDigest(model.asset.sizeBytes, model.asset.sha256)) { "GLB does not match metadata: ${model.modelId}" }
            val key = "releases/$id/models/${model.modelId}/${model.asset.sha256}.glb"
            PreparedModel(model.copy(asset = model.asset.copy(path = key)), staged)
        }
        return jobs.register(id, requestKey, manifest.datasetVersion, fingerprint, prepared)
    }
}
