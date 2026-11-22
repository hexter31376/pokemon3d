package com.pokemon3d.api.assets.infrastructure.storage.s3

import com.pokemon3d.api.assets.application.digest
import com.pokemon3d.api.publishing.infrastructure.manifest.ManifestJson
import com.pokemon3d.api.publishing.infrastructure.manifest.ModelManifest
import com.pokemon3d.api.publishing.infrastructure.manifest.tools.ManifestWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

internal fun createDataset(root: Path, marker: String = ""): Pair<Path, ModelManifest> {
    val resource = StorageUnitTests::class.java.getResource("/manifest/valid.json")!!
    val manifest = ManifestJson.read(Path.of(resource.toURI()))
    val json = """{"asset":{"version":"2.0"},"scenes":[{}],"scene":0,"extras":{"marker":"$marker"}}""".toByteArray()
    val padding = (4 - json.size % 4) % 4
    val bytes = ByteBuffer.allocate(20 + json.size + padding)
        .order(ByteOrder.LITTLE_ENDIAN)
    bytes.putInt(0x46546c67)
        .putInt(2)
        .putInt(bytes.capacity())
        .putInt(json.size + padding)
        .putInt(0x4e4f534a)
        .put(json)
    repeat(padding) { bytes.put(32) }
    val file = root.resolve("models/fixture_001.glb")
    Files.createDirectories(file.parent); Files.write(file, bytes.array())
    val hash = Files.newInputStream(file).use(::digest)
    val model = manifest.models.single()
    val result = manifest.copy(models = listOf(model.copy(asset = model.asset.copy(sizeBytes = hash.sizeBytes, sha256 = hash.sha256))))
    val path = root.resolve("manifest.json"); ManifestWriter.writeAtomic(path, result)
    return path to result
}
