package com.pokemon3d.api.model3d.infrastructure.storage.s3

import com.pokemon3d.api.model3d.domain.*
import com.pokemon3d.api.model3d.domain.exception.ModelStorageException
import com.pokemon3d.api.model3d.domain.exception.StorageFailure
import com.pokemon3d.api.model3d.infrastructure.manifest.ManifestJson
import com.pokemon3d.api.model3d.infrastructure.manifest.ModelManifest
import com.pokemon3d.api.model3d.infrastructure.manifest.tools.ManifestWriter
import com.pokemon3d.api.model3d.infrastructure.storage.s3.tools.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

internal fun createDataset(root: Path): Pair<Path, ModelManifest> {
    val resource = DatasetUploaderTests::class.java.getResource("/manifest/valid.json")!!
    val manifest = ManifestJson.read(Path.of(resource.toURI()))
    val json = """{"asset":{"version":"2.0"},"scenes":[{}],"scene":0}""".toByteArray()
    val padding = (4 - json.size % 4) % 4
    val bytes = ByteBuffer.allocate(20 + json.size + padding).order(ByteOrder.LITTLE_ENDIAN)
    bytes.putInt(0x46546c67).putInt(2).putInt(bytes.capacity()).putInt(json.size + padding).putInt(0x4e4f534a).put(json)
    repeat(padding) { bytes.put(32) }
    val file = root.resolve("models/fixture_001.glb")
    Files.createDirectories(file.parent); Files.write(file, bytes.array())
    val hash = Files.newInputStream(file).use(::digest)
    val model = manifest.models.single()
    val result = manifest.copy(models = listOf(model.copy(asset = model.asset.copy(sizeBytes = hash.sizeBytes, sha256 = hash.sha256))))
    val path = root.resolve("manifest.json"); ManifestWriter.writeAtomic(path, result)
    return path to result
}

class DatasetUploaderTests {
    @TempDir lateinit var root: Path

    private class MemoryStore : DatasetObjectStore {
        val data = mutableMapOf<String, ByteArray>()
        val metadata = mutableMapOf<String, StoredModelAsset>()
        val writes = mutableListOf<String>()
        var breakRead = false
        override fun head(key: String) = metadata[key]
        override fun put(key: String, file: Path, contentType: String, sha256: String) {
            writes += key; data[key] = Files.readAllBytes(file)
            metadata[key] = StoredModelAsset(data.getValue(key).size.toLong(), contentType, sha256)
        }
        override fun contentDigest(key: String): ContentDigest =
            if (breakRead) ContentDigest(0, "wrong") else data.getValue(key).inputStream().use(::digest)
    }

    @Test
    fun `upload publishes manifest last and repeated upload skips verified bytes`() {
        val (path, _) = createDataset(root)
        val store = MemoryStore(); val tool = ModelDatasetUploader(store, ObjectKeyResolver("pokemon"))
        assertEquals(TransferReport(2, 0, 2), tool.execute(root, path, true))
        assertEquals("pokemon/manifest.json", store.writes.last())
        assertEquals(TransferReport(0, 2, 2), tool.execute(root, path, true))
        assertEquals(TransferReport(0, 0, 2), tool.execute(root, path, false))
        assertEquals(2, store.writes.size)
    }

    @Test
    fun `remote corruption conflicts by default and overwrite repairs it`() {
        val (path, _) = createDataset(root)
        val store = MemoryStore(); val tool = ModelDatasetUploader(store, ObjectKeyResolver(""))
        tool.execute(root, path, true)
        store.data["models/fixture_001.glb"] = byteArrayOf(1)
        val conflict = assertFailsWith<DatasetTransferException> { tool.execute(root, path, true) }
        assertEquals(StorageFailure.CONFLICT, (conflict.cause as ModelStorageException).failure)
        assertEquals(TransferReport(1, 1, 2), tool.execute(root, path, true, true))
    }

    @Test
    fun `local mismatch stops all writes and remote verification catches corruption`() {
        val (path, _) = createDataset(root)
        val store = MemoryStore(); val tool = ModelDatasetUploader(store, ObjectKeyResolver(""))
        Files.writeString(root.resolve("models/fixture_001.glb"), "changed")
        assertFailsWith<IllegalArgumentException> { tool.execute(root, path, true) }
        assertTrue(store.writes.isEmpty())
        createDataset(root); store.breakRead = true
        val failure = assertFailsWith<DatasetTransferException> { tool.execute(root, path, true) }
        assertEquals(StorageFailure.INTEGRITY_MISMATCH, (failure.cause as ModelStorageException).failure)
        assertFalse("manifest.json" in store.writes)
    }

    @Test
    fun `verify reports missing object and never uploads`() {
        val (path, _) = createDataset(root)
        val store = MemoryStore(); val tool = ModelDatasetUploader(store, ObjectKeyResolver(""))
        val failure = assertFailsWith<DatasetTransferException> { tool.execute(root, path, false) }
        assertEquals(StorageFailure.OBJECT_NOT_FOUND, (failure.cause as ModelStorageException).failure)
        assertTrue(store.writes.isEmpty())
    }
}
