package com.pokemon3d.api.model3d.infrastructure.storage.s3.tools

import com.pokemon3d.api.model3d.domain.exception.ModelStorageException
import com.pokemon3d.api.model3d.domain.exception.StorageFailure
import com.pokemon3d.api.model3d.infrastructure.manifest.ManifestJson
import com.pokemon3d.api.model3d.infrastructure.storage.s3.*
import java.nio.file.Files
import java.nio.file.Path

internal data class TransferReport(val uploaded: Int, val skipped: Int, val verified: Int)
internal class DatasetTransferException(val report: TransferReport, val key: String, cause: Exception) :
    RuntimeException("Dataset transfer failed: key=$key, uploaded=${report.uploaded}, skipped=${report.skipped}, verified=${report.verified}, failed=1", cause)

internal class ModelDatasetUploader(private val objects: DatasetObjectStore, private val keys: ObjectKeyResolver) {
    private data class FileEntry(val path: Path, val key: String, val contentType: String, val content: ContentDigest)

    fun execute(root: Path, manifestPath: Path, upload: Boolean, overwrite: Boolean = false): TransferReport {
        require(upload || !overwrite) { "--overwrite requires upload" }
        val manifest = ManifestJson.read(manifestPath)
        val dataRoot = root.toRealPath()
        val listed = manifest.models.map { it.asset.path }.toSet()
        val actual = Files.walk(dataRoot.resolve("models")).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".glb") }
                .map { dataRoot.relativize(it).toString().replace('\\', '/') }.toList().toSet()
        }
        require(listed == actual) { "Local GLB file set differs from manifest" }
        // Finish the entire local preflight before any remote access or writes.
        val entries = manifest.models.map { model ->
            val file = dataRoot.resolve(model.asset.path).toRealPath()
            require(file.startsWith(dataRoot)) { "Model file escapes data root: ${model.modelId}" }
            val content = Files.newInputStream(file).use(::digest)
            require(content == ContentDigest(model.asset.sizeBytes, model.asset.sha256)) { "Local GLB differs from manifest: ${model.modelId}" }
            FileEntry(file, keys.resolve(model.asset.path), "model/gltf-binary", content)
        } + FileEntry(manifestPath, keys.resolve("manifest.json"), "application/json", Files.newInputStream(manifestPath).use(::digest))
        var uploaded = 0; var skipped = 0; var verified = 0
        for (entry in entries) {
            try {
                val head = objects.head(entry.key)
                val matches = head != null && head.sizeBytes == entry.content.sizeBytes && head.contentType == entry.contentType
                    && head.sha256 == entry.content.sha256 && objects.contentDigest(entry.key) == entry.content
                if (matches) {
                    if (upload) skipped++
                } else {
                    if (!upload) throw ModelStorageException(if (head == null) StorageFailure.OBJECT_NOT_FOUND else StorageFailure.INTEGRITY_MISMATCH, entry.key)
                    if (head != null && !overwrite) throw ModelStorageException(StorageFailure.CONFLICT, entry.key)
                    require(Files.newInputStream(entry.path).use(::digest) == entry.content) { "Local file changed during upload: ${entry.key}" }
                    objects.put(entry.key, entry.path, entry.contentType, entry.content.sha256)
                    uploaded++
                    val after = objects.head(entry.key)
                    if (after == null || after.sizeBytes != entry.content.sizeBytes || after.contentType != entry.contentType
                        || after.sha256 != entry.content.sha256 || objects.contentDigest(entry.key) != entry.content) {
                        throw ModelStorageException(StorageFailure.INTEGRITY_MISMATCH, entry.key)
                    }
                }
                verified++
            } catch (ex: Exception) {
                throw DatasetTransferException(TransferReport(uploaded, skipped, verified), entry.key, ex)
            }
        }
        return TransferReport(uploaded, skipped, verified)
    }
}
