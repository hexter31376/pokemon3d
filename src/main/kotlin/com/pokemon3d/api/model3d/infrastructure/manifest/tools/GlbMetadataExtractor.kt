package com.pokemon3d.api.model3d.infrastructure.manifest.tools

import com.pokemon3d.api.model3d.domain.ModelAsset
import com.pokemon3d.api.model3d.domain.ModelBounds
import com.pokemon3d.api.model3d.domain.ModelCamera
import com.pokemon3d.api.model3d.domain.ModelForm
import com.pokemon3d.api.model3d.domain.ModelMetadata
import com.pokemon3d.api.model3d.domain.ModelMotion
import com.pokemon3d.api.model3d.infrastructure.manifest.ManifestJson
import com.pokemon3d.api.model3d.infrastructure.manifest.ManifestValidator
import com.pokemon3d.api.model3d.infrastructure.manifest.ModelManifest
import tools.jackson.databind.JsonNode
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.math.abs

internal class GlbMetadataExtractor {
    fun extract(root: Path, catalog: Path, datasetVersion: String, expectedCount: Int? = null): ModelManifest {
        val entries = ManifestJson.mapper.readTree(catalog.toFile()).array("catalog")
        require(entries.isNotEmpty()) { "$catalog: catalog must not be empty" }
        require(expectedCount == null || entries.size == expectedCount) { "$catalog: expected $expectedCount models, found ${entries.size}" }
        val listed = entries.map { it.required("file").text("file") }
        require(listed.distinct().size == listed.size) { "$catalog: duplicate file paths" }
        val actual = Files.walk(root.resolve("models")).use { files ->
            files.filter { Files.isRegularFile(it) && it.toString().endsWith(".glb") }
                .map { root.relativize(it).toString().replace('\\', '/') }.toList().toSet()
        }
        require(listed.toSet() == actual) {
            "$root: file set mismatch: missing=${(listed.toSet() - actual).take(5)}, unlisted=${(actual - listed.toSet()).take(5)}"
        }
        val models = entries.sortedBy { it.required("id").text("id") }.map { entry ->
            try { extractModel(entry, root) } catch (ex: Exception) {
                throw IllegalArgumentException("${entry.path("id")} (${entry.path("file")}): ${ex.message}", ex)
            }
        }
        return ModelManifest(1, datasetVersion, models.size, models).also(ManifestValidator::validate)
    }

    private fun extractModel(e: JsonNode, root: Path): ModelMetadata {
        val relative = e.required("file").text("file")
        val path = root.resolve(relative).toRealPath()
        require(path.startsWith(root.toRealPath())) { "path escapes data root" }
        require(e.path("status").asString() == "ok") { "catalog status must be ok" }
        val category = e.required("category").text("category")
        require(category in setOf("normal", "shiny")) { "unknown category" }
        val generation = e.required("generation").text("generation")
        require(generation.matches(Regex("gen[1-9][0-9]*"))) { "invalid generation" }
        val generationNumber = generation.substring(3).toIntOrNull()
        require(generationNumber != null) { "generation exceeds JVM Int range" }
        val number = e.required("number").int("number")
        val gltf = readGlb(path)
        val c = gltf.required("extras").required("viewerCamera")
        require(c.required("version").int("viewerCamera.version") == 1) { "unsupported viewerCamera version" }
        val node = gltf.required("nodes").element(c.required("cameraNode"), "cameraNode")
        val embedded = gltf.required("cameras").element(node.required("camera"), "camera")
        val name = c.required("name").text("viewerCamera.name")
        require(embedded.path("name").asString() == name && embedded.path("type").asString() == "perspective") { "embedded camera mismatch" }
        val fov = c.required("fovDegrees").number("fovDegrees")
        require(abs(Math.toDegrees(embedded.required("perspective").required("yfov").number("yfov")) - fov) <= 1e-5) { "camera fov mismatch" }
        val roles = mapOf("대기" to "idle", "울음" to "cry", "물리공격" to "physical_attack", "특수공격" to "special_attack",
            "메가진화 포즈" to "pose", "불꽃 반복" to "effect", "가라르 구름" to "effect")
        val motions = (gltf.get("animations")?.array("animations") ?: emptyList()).mapIndexed { index, animation ->
            val times = animation.required("samplers").array("samplers").map { sampler ->
                val accessor = gltf.required("accessors").element(sampler.required("input"), "sampler.input")
                require(accessor.path("type").asString() == "SCALAR" && accessor.required("componentType").int("componentType") == 5126
                    && accessor.required("count").int("count") > 0) { "invalid animation time accessor" }
                val min = accessor.required("min").array("min")
                val max = accessor.required("max").array("max")
                require(min.size == 1 && max.size == 1) { "time accessor requires scalar min/max" }
                val start = min.single().number("animation.min")
                val end = max.single().number("animation.max")
                require(start >= 0 && end >= start) { "invalid animation time range" }
                start to end
            }
            require(times.isNotEmpty()) { "animation has no samplers" }
            val start = times.minOf { it.first }
            val end = times.maxOf { it.second }
            val animationName = animation.get("name")?.takeUnless { it.isNull || it.asString().isEmpty() }?.text("animation.name") ?: "animation_$index"
            val role = animation.path("extras").get("role")?.takeUnless { it.isNull || it.asString().isEmpty() }?.text("animation.role")
                ?: roles[animationName] ?: "other"
            ModelMotion(index, animationName, role, start, end, end - start)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) { val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
        }
        val id = e.required("id").text("id")
        val formCode = e.required("formCode").takeUnless { it.isNull || it.asString().isEmpty() }?.text("formCode") ?: id.removeSuffix("_rare")
        return ModelMetadata(
            id, number, e.required("ko").text("ko"), e.required("en").text("en"),
            ModelForm(formCode, e.required("formKind").text("formKind"), e.required("formLabel").text("formLabel")),
            category == "shiny", e.required("gender").text("gender"), generationNumber,
            ModelAsset(
                relative, Files.size(path), digest.digest().joinToString("") { "%02x".format(it) },
                (gltf.get("extensionsRequired")?.array("extensionsRequired")
                    ?: emptyList()).map { it.text("extension") }.sorted()
            ),
            motions, ModelCamera(
                name, c.required("direction").vector("direction"), fov,
                c.required("padding").number("padding"), c.required("target").vector("target"),
                ModelBounds(
                    c.required("bounds").required("min").vector("bounds.min"),
                    c.required("bounds").required("max").vector("bounds.max")
                )
            )
        )
    }

    internal fun readGlb(path: Path): JsonNode = RandomAccessFile(path.toFile(), "r").use { file ->
        fun uint(): Long = Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()))
        require(file.length() >= 12) { "truncated GLB header" }
        require(uint() == 0x46546c67L && uint() == 2L && uint() == file.length()) { "invalid GLB header/length" }
        var document: JsonNode? = null
        while (file.filePointer < file.length()) {
            val first = file.filePointer == 12L
            require(file.length() - file.filePointer >= 8) { "truncated chunk header" }
            val size = uint(); val kind = uint()
            require(size % 4 == 0L && size <= file.length() - file.filePointer) { "invalid chunk length" }
            if (first) {
                require(kind == 0x4e4f534aL && size <= Int.MAX_VALUE) { "first chunk must be readable JSON" }
                val bytes = ByteArray(size.toInt()); file.readFully(bytes)
                document = ManifestJson.mapper.readTree(bytes)
            } else {
                require(kind != 0x4e4f534aL) { "duplicate JSON chunk" }
                file.seek(file.filePointer + size)
            }
        }
        require(document != null && document.path("asset").path("version").asString() == "2.0") { "missing glTF 2.0 JSON" }
        document
    }
}

private fun JsonNode.text(at: String): String {
    require(isString && asString().isNotBlank()) { "$at: must be a nonblank string" }
    return asString()
}
private fun JsonNode.int(at: String): Int {
    require(isIntegralNumber && canConvertToInt()) { "$at: must be a JVM Int" }
    return intValue()
}
private fun JsonNode.number(at: String): Double {
    require(isNumber && doubleValue().isFinite()) { "$at: must be finite" }
    return doubleValue()
}
private fun JsonNode.array(at: String): List<JsonNode> {
    require(isArray) { "$at: must be an array" }
    return toList()
}
private fun JsonNode.vector(at: String): List<Double> {
    val values = array(at)
    require(values.size == 3) { "$at: must contain 3 numbers" }
    return values.map { it.number(at) }
}
private fun JsonNode.element(index: JsonNode, at: String): JsonNode {
    val i = index.int(at)
    require(isArray && i >= 0 && i < size()) { "$at: invalid index $i" }
    return get(i)
}
