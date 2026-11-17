package com.pokemon3d.api.model3d.infrastructure.manifest

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import kotlin.test.*

class JsonManifestModelMetadataRepositoryTests {
    @TempDir lateinit var temp: Path
    private val mapper = JsonMapper.builder()
        .build()
    private fun fixture(): String = javaClass.getResource("/manifest/valid.json")!!
        .readText()
    private fun write(text: String): Path = Files.writeString(temp.resolve("manifest.json"), text)
    private fun modified(change: (ObjectNode) -> Unit): Path {
        val doc = mapper.readTree(fixture()) as ObjectNode
        change(doc)
        return write(mapper.writeValueAsString(doc))
    }
    private fun model(doc: ObjectNode) = doc["models"][0] as ObjectNode
    private fun fails(path: Path, reason: String) {
        val ex = assertFailsWith<ManifestLoadException> { JsonManifestModelMetadataRepository(path) }
        assertContains(ex.message!!, path.toString())
        assertContains(ex.message!!, reason)
        assertNotNull(ex.cause)
    }

    @Test
    fun `loads metadata motions camera and indexes without GLB files`() {
        val repository = JsonManifestModelMetadataRepository(write(fixture()))
        assertEquals("fixture-v1", repository.datasetVersion)
        val model = assertNotNull(repository.findById("fixture_001"))
        assertEquals("테스트몬", model.nameKo)
        assertEquals("idle", model.motions.single().role)
        assertEquals(2.0, model.motions.single().durationSeconds)
        assertEquals("Pokemon_Left_Oblique", model.camera.cameraName)
        assertEquals(35.0, model.camera.fov)
        assertEquals(listOf(model), repository.findByPokedexNumber(1))
        assertNull(repository.findById("unknown"))
        assertTrue(repository.findByPokedexNumber(999).isEmpty())
        assertEquals(listOf(model), repository.findAll())
    }

    @Test
    fun `snapshot and nested lists cannot be mutated and file is read only once`() {
        val path = write(fixture())
        val repository = JsonManifestModelMetadataRepository(path)
        assertFailsWith<UnsupportedOperationException> { (repository.findAll() as MutableList).clear() }
        val model = repository.findAll()
            .single()
        assertFailsWith<UnsupportedOperationException> { (model.camera.direction as MutableList)[0] = 99.0 }
        assertFailsWith<UnsupportedOperationException> { (model.motions as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (model.asset.requiredExtensions as MutableList).clear() }
        Files.writeString(path, "invalid")
        val loadedModel = repository.findAll()
            .single()
        assertEquals("fixture_001", loadedModel.modelId)
    }

    @Test
    fun `rejects duplicate IDs`() {
        fails(modified { doc ->
            val models = doc["models"] as ArrayNode
            models.add(models[0].deepCopy())
            doc.put("modelCount", 2)
        }, "duplicate ID")
    }

    @Test
    fun `rejects missing string boolean and numeric fields`() {
        for (field in listOf("nameKo", "shiny", "generation")) {
            fails(modified { model(it).remove(field) }, field)
        }
    }

    @Test
    fun `rejects null and coerced values`() {
        fails(modified { model(it).putNull("nameKo") }, "nameKo")
        fails(modified { model(it).putNull("shiny") }, "shiny")
        fails(modified { model(it).put("generation", "1") }, "generation")
    }

    @Test
    fun `rejects unsupported version count unknown fields and malformed JSON`() {
        fails(modified { it.put("schemaVersion", 2) }, "schemaVersion")
        fails(modified { it.put("modelCount", 2) }, "modelCount")
        fails(modified { it.put("typo", true) }, "typo")
        assertFailsWith<ManifestLoadException> { JsonManifestModelMetadataRepository(write("{")) }
        assertFailsWith<ManifestLoadException> { JsonManifestModelMetadataRepository(temp.resolve("missing.json")) }
    }

    @Test
    fun `rejects invalid camera vectors fov padding bounds and target`() {
        val changes: List<Pair<String, (ObjectNode) -> Unit>> = listOf(
            "direction" to {
                it.putArray("direction")
                    .add(0)
                    .add(0)
                    .add(0)
            },
            "direction" to {
                it.putArray("direction")
                    .add(0)
                    .add(1)
                    .add(0)
            },
            "target" to {
                it.putArray("target")
                    .add(0)
                    .add(1)
            },
            "target" to {
                it.putArray("target")
                    .add(100)
                    .add(100)
                    .add(100)
            },
            "fov" to { it.put("fov", 180) },
            "padding" to { it.put("padding", 0) },
            "bounds" to {
                (it["bounds"] as ObjectNode)
                    .putArray("min")
                    .add(2)
                    .add(3)
                    .add(2)
            },
        )
        for ((reason, change) in changes) {
            fails(modified { change(model(it)["camera"] as ObjectNode) }, reason)
        }
    }

    @Test
    fun `rejects unsafe asset paths and invalid motion duration`() {
        fails(
            modified {
                val asset = model(it)["asset"] as ObjectNode
                asset.put("path", "../secret.glb")
            },
            "asset.path",
        )
        fails(modified { (model(it)["motions"][0] as ObjectNode).put("durationSeconds", -1) }, "motions[0]")
    }
}
