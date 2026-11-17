package com.pokemon3d.api.model3d.infrastructure.manifest.tools

import com.pokemon3d.api.model3d.infrastructure.manifest.ManifestJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.node.ObjectNode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import kotlin.test.*

class ModelManifestToolsTests {
    @TempDir lateinit var root: Path
    private val mapper = ManifestJson.mapper
    private val extractor = GlbMetadataExtractor()
    private lateinit var gltf: ObjectNode
    private lateinit var entry: ObjectNode
    private val glb get() = root.resolve("models/fixture_001.glb")
    private val catalog get() = root.resolve("catalog.json")
    private val output get() = root.resolve("manifest.json")

    @BeforeEach
    fun prepare() {
        Files.createDirectories(glb.parent)
        gltf = mapper.readTree("""{
          "asset":{"version":"2.0"},
          "extras":{"viewerCamera":{"version":1,"name":"Pokemon_Left_Oblique","direction":[0,0,1],
            "fovDegrees":35,"padding":1.18,"target":[0,1,0],"bounds":{"min":[-1,0,-1],"max":[1,2,1]},"cameraNode":0}},
          "nodes":[{"camera":0}],
          "cameras":[{"name":"Pokemon_Left_Oblique","type":"perspective","perspective":{"yfov":0.6108652381980153}}],
          "accessors":[{"type":"SCALAR","componentType":5126,"count":2,"min":[1],"max":[3]}],
          "animations":[{"name":"대기","samplers":[{"input":0}]}],
          "extensionsRequired":["EXT_meshopt_compression"]
        }""") as ObjectNode
        entry = mapper.readTree("""{"id":"fixture_001","number":1,"ko":"테스트몬","en":"Fixturemon",
          "formCode":"00","formKind":"base","formLabel":"일반 모습","category":"normal","gender":"shared",
          "generation":"gen1","file":"models/fixture_001.glb","status":"ok","bytes":999999}""") as ObjectNode
        writeGlb(); writeCatalog()
    }

    private fun writeCatalog() { Files.writeString(catalog, mapper.writeValueAsString(listOf(entry))) }
    private fun writeGlb() {
        val json = mapper.writeValueAsBytes(gltf)
        val padding = (4 - json.size % 4) % 4
        val buffer = ByteBuffer.allocate(20 + json.size + padding).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x46546c67).putInt(2).putInt(buffer.capacity()).putInt(json.size + padding).putInt(0x4e4f534a)
        buffer.put(json); repeat(padding) { buffer.put(32) }
        Files.write(glb, buffer.array())
    }
    private fun extract() = extractor.extract(root, catalog, "fixture-v1", 1)
    private fun cli(command: String) = arrayOf(command, "--data-root", root.toString(), "--catalog", catalog.toString(),
        "--manifest", output.toString(), "--expected-count", "1")

    @Test
    fun `extracts actual size hash times role and camera`() {
        val model = extract().models.single()
        assertEquals(Files.size(glb), model.asset.sizeBytes)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(glb)).joinToString("") { "%02x".format(it) }, model.asset.sha256)
        assertEquals(1.0, model.motions.single().startTimeSeconds)
        assertEquals(2.0, model.motions.single().durationSeconds)
        assertEquals("idle", model.motions.single().role)
        assertEquals("Pokemon_Left_Oblique", model.camera.cameraName)
        assertEquals(listOf(0.0, 0.0, 1.0), model.camera.direction)
    }

    @Test
    fun `repeat runs are deterministic and CLI verifies result`() {
        ModelManifestCli.run(cli("extract") + arrayOf("--dataset-version", "fixture-v1"))
        val before = Files.readAllBytes(output)
        ModelManifestCli.run(cli("extract") + arrayOf("--dataset-version", "fixture-v1"))
        assertContentEquals(before, Files.readAllBytes(output))
        assertContains(ModelManifestCli.run(cli("verify")), "1 models")
        assertEquals(extract(), ManifestJson.read(output))
    }

    @Test
    fun `unknown animation and missing source form code are preserved`() {
        (gltf["animations"][0] as ObjectNode).put("name", "new animation")
        entry.putNull("formCode"); writeCatalog(); writeGlb()
        val model = extract().models.single()
        assertEquals("other", model.motions.single().role)
        assertEquals("fixture_001", model.form.code)
    }

    @Test
    fun `bad header and missing animation metadata fail with model context`() {
        Files.writeString(glb, "bad")
        assertContains(assertFailsWith<IllegalArgumentException> { extract() }.message!!, "fixture_001")
        (gltf["accessors"][0] as ObjectNode).remove("max"); writeGlb()
        assertContains(assertFailsWith<IllegalArgumentException> { extract() }.message!!, "fixture_001")
        gltf.remove("extras"); writeGlb()
        assertFailsWith<IllegalArgumentException> { extract() }
    }

    @Test
    fun `missing extra and duplicate files are rejected`() {
        Files.delete(glb)
        assertContains(assertFailsWith<IllegalArgumentException> { extract() }.message!!, "file set mismatch")
        writeGlb(); Files.copy(glb, glb.resolveSibling("extra.glb"))
        assertContains(assertFailsWith<IllegalArgumentException> { extract() }.message!!, "unlisted")
        Files.writeString(catalog, mapper.writeValueAsString(listOf(entry, entry)))
        assertContains(assertFailsWith<IllegalArgumentException> { extractor.extract(root, catalog, "v1") }.message!!, "duplicate file")
    }

    @Test
    fun `changed binary is detected even if JSON metadata is identical`() {
        ModelManifestCli.run(cli("extract") + arrayOf("--dataset-version", "v1"))
        val before = extract()
        // Add a valid unknown chunk: metadata stays identical but bytes differ.
        val original = Files.readAllBytes(glb)
        val updated = ByteBuffer.allocate(original.size + 12).order(ByteOrder.LITTLE_ENDIAN)
        updated.put(original).putInt(4).putInt(0x12345678).putInt(0)
        updated.putInt(8, updated.capacity()); Files.write(glb, updated.array())
        assertNotEquals(before.models.single().asset.sha256, extract().models.single().asset.sha256)
        assertFailsWith<IllegalArgumentException> { ModelManifestCli.run(cli("verify")) }
    }

    @Test
    fun `extractor and reader agree on JVM integer limits`() {
        entry.put("generation", "gen2147483647"); entry.put("number", Int.MAX_VALUE); writeCatalog()
        val max = extract(); ManifestWriter.writeAtomic(output, max)
        assertEquals(max, ManifestJson.read(output))
        entry.put("generation", "gen2147483648"); writeCatalog()
        assertContains(assertFailsWith<IllegalArgumentException> { extract() }.message!!, "generation")
        entry.put("generation", "gen1"); entry.put("number", 2147483648L); writeCatalog()
        assertContains(assertFailsWith<IllegalArgumentException> { extract() }.message!!, "number")
    }

    @Test
    fun `schema and reader enforce Int and Long boundaries`() {
        val model = extract().models.single()
        val manifest = extract().copy(models = listOf(model.copy(asset = model.asset.copy(sizeBytes = Long.MAX_VALUE))))
        ManifestWriter.writeAtomic(output, manifest)
        assertEquals(Long.MAX_VALUE, ManifestJson.read(output).models.single().asset.sizeBytes)
        val original = mapper.readTree(output.toFile()) as ObjectNode
        for (field in listOf("generation", "pokedexNumber")) {
            val doc = original.deepCopy()
            (doc["models"][0] as ObjectNode).put(field, 2147483648L)
            Files.writeString(output, mapper.writeValueAsString(doc))
            assertFails { ManifestJson.read(output) }
        }
        (original["models"][0]["asset"] as ObjectNode).put("sizeBytes", java.math.BigInteger("9223372036854775808"))
        Files.writeString(output, mapper.writeValueAsString(original)); assertFails { ManifestJson.read(output) }
        val schema = mapper.readTree(Path.of("docs/model-manifest.schema.json").toFile())
        val properties = schema["properties"]["models"]["items"]["properties"]
        assertEquals(Int.MAX_VALUE.toLong(), properties["generation"]["maximum"].longValue())
        assertEquals(Int.MAX_VALUE.toLong(), properties["pokedexNumber"]["maximum"].longValue())
        assertEquals(Int.MAX_VALUE.toLong(), schema["properties"]["modelCount"]["maximum"].longValue())
        assertEquals(Int.MAX_VALUE.toLong(), properties["motions"]["items"]["properties"]["index"]["maximum"].longValue())
        assertEquals(Long.MAX_VALUE, properties["asset"]["properties"]["sizeBytes"]["maximum"].longValue())
    }

    @Test
    fun `existing permissions are preserved and new manifest is readable`() {
        assumeTrue(Files.getFileAttributeView(root, PosixFileAttributeView::class.java) != null)
        ManifestWriter.writeAtomic(output, extract())
        assertEquals("rw-r--r--", PosixFilePermissions.toString(Files.getPosixFilePermissions(output)))
        for (mode in listOf("rw-r--r--", "rw-r-----", "rw-------")) {
            Files.setPosixFilePermissions(output, PosixFilePermissions.fromString(mode))
            ManifestWriter.writeAtomic(output, extract())
            assertEquals(mode, PosixFilePermissions.toString(Files.getPosixFilePermissions(output)))
        }
    }

    @Test
    fun `failed replacement preserves destination and removes temporary file`() {
        Files.createDirectory(output)
        val marker = Files.writeString(output.resolve("keep"), "original")
        assertFails { ManifestWriter.writeAtomic(output, extract()) }
        assertEquals("original", Files.readString(marker))
        Files.list(root).use { paths -> assertFalse(paths.anyMatch { it.fileName.toString().startsWith(".manifest-") }) }
    }

    @Test
    fun `invalid camera and duplicate model ID use shared validator`() {
        (gltf["extras"]["viewerCamera"] as ObjectNode).put("fovDegrees", 180); writeGlb()
        assertFailsWith<IllegalArgumentException> { extract() }
        val manifest = ManifestJson.read(Path.of(javaClass.getResource("/manifest/valid.json")!!.toURI()))
        assertFailsWith<IllegalArgumentException> {
            ManifestWriter.writeAtomic(output, manifest.copy(modelCount = 2, models = manifest.models + manifest.models))
        }
        assertFalse(Files.exists(output))
    }
}
