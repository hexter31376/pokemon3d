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
    private companion object {
        const val CAMERA_FOV_TOLERANCE = 1e-5
        const val FLOAT_COMPONENT_TYPE = 5126
        const val GLB_HEADER_SIZE = 12L
        const val GLB_MAGIC = 0x46546c67L
        const val GLB_VERSION = 2L
        const val GLTF_VERSION = "2.0"
        const val JSON_CHUNK_TYPE = 0x4e4f534aL
        const val SCALAR_ACCESSOR_TYPE = "SCALAR"
        const val PERSPECTIVE_CAMERA_TYPE = "perspective"
        const val DEFAULT_MOTION_ROLE = "other"
        const val HASH_BUFFER_SIZE = 1024 * 1024
        val GENERATION_PATTERN = Regex("gen[1-9][0-9]*")
    }

    private val knownMotionRoles = mapOf(
        "대기" to "idle",
        "울음" to "cry",
        "물리공격" to "physical_attack",
        "특수공격" to "special_attack",
        "메가진화 포즈" to "pose",
        "불꽃 반복" to "effect",
        "가라르 구름" to "effect",
    )

    fun extract(root: Path, catalog: Path, datasetVersion: String, expectedCount: Int? = null): ModelManifest {
        val entries = ManifestJson.mapper
            .readTree(catalog.toFile())
            .array("catalog")
        require(entries.isNotEmpty()) { "$catalog: catalog must not be empty" }
        require(matchesExpectedCount(entries.size, expectedCount)) {
            "$catalog: expected $expectedCount models, found ${entries.size}"
        }
        val listed = entries.map { entry ->
            entry.required("file")
                .text("file")
        }
        val hasUniquePaths = listed.distinct()
            .size == listed.size
        require(hasUniquePaths) { "$catalog: duplicate file paths" }
        val actual = Files.walk(root.resolve("models"))
            .use { files ->
                files.filter { file ->
                    val isGlb = file.toString()
                        .endsWith(".glb")
                    Files.isRegularFile(file) && isGlb
                }
                .map { file ->
                    root.relativize(file)
                        .toString()
                        .replace('\\', '/')
                }
                .toList()
                .toSet()
            }
        val listedPaths = listed.toSet()
        val missingPaths = (listedPaths - actual).take(5)
        val unlistedPaths = (actual - listedPaths).take(5)
        require(listedPaths == actual) {
            "$root: file set mismatch: missing=$missingPaths, unlisted=$unlistedPaths"
        }
        val models = entries
            .sortedBy { it.required("id").text("id") }
            .map { entry ->
                try {
                    extractModel(entry, root)
                } catch (ex: Exception) {
                    throw IllegalArgumentException(
                        "${entry.path("id")} (${entry.path("file")}): ${ex.message}",
                        ex,
                    )
                }
            }
        return ModelManifest(1, datasetVersion, models.size, models)
            .also(ManifestValidator::validate)
    }

    private fun extractModel(e: JsonNode, root: Path): ModelMetadata {
        val relative = e.required("file")
            .text("file")
        val path = root.resolve(relative)
            .toRealPath()
        require(path.startsWith(root.toRealPath())) { "path escapes data root" }
        require(e.path("status").asString() == "ok") { "catalog status must be ok" }
        val category = e.required("category")
            .text("category")
        require(category in setOf("normal", "shiny")) { "unknown category" }
        val generation = e.required("generation")
            .text("generation")
        require(generation.matches(GENERATION_PATTERN)) { "invalid generation" }
        val generationNumber = generation.substring(3)
            .toIntOrNull()
        require(generationNumber != null) { "generation exceeds JVM Int range" }
        val number = e.required("number").int("number")
        val gltf = readGlb(path)
        val camera = extractCamera(gltf)
        val motions = extractMotions(gltf)
        val id = e.required("id")
            .text("id")
        val formCode = e.required("formCode")
            .takeUnless { it.isNull || it.asString().isEmpty() }
            ?.text("formCode")
            ?: id.removeSuffix("_rare")
        return ModelMetadata(
            id,
            number,
            e.required("ko").text("ko"),
            e.required("en").text("en"),
            ModelForm(
                formCode,
                e.required("formKind").text("formKind"),
                e.required("formLabel").text("formLabel"),
            ),
            category == "shiny",
            e.required("gender").text("gender"),
            generationNumber,
            extractAsset(relative, path, gltf), motions, camera,
        )
    }

    private fun extractCamera(gltf: JsonNode): ModelCamera {
        val camera = gltf.required("extras")
            .required("viewerCamera")
        val cameraVersion = camera.required("version")
            .int("viewerCamera.version")
        require(cameraVersion == 1) { "unsupported viewerCamera version" }
        val node = gltf.required("nodes")
            .element(camera.required("cameraNode"), "cameraNode")
        val embedded = gltf.required("cameras")
            .element(node.required("camera"), "camera")
        val name = camera.required("name")
            .text("viewerCamera.name")
        val embeddedName = embedded.path("name").asString()
        val embeddedType = embedded.path("type").asString()
        require(matchesEmbeddedPerspectiveCamera(embeddedName, embeddedType, name)) {
            "embedded camera mismatch"
        }
        val fov = camera.required("fovDegrees")
            .number("fovDegrees")
        val embeddedFov = Math.toDegrees(
            embedded.required("perspective")
                .required("yfov")
                .number("yfov"),
        )
        require(abs(embeddedFov - fov) <= CAMERA_FOV_TOLERANCE) { "camera fov mismatch" }
        val bounds = camera.required("bounds")
        return ModelCamera(
            name,
            camera.required("direction").vector("direction"),
            fov,
            camera.required("padding").number("padding"),
            camera.required("target").vector("target"),
            ModelBounds(
                bounds.required("min")
                    .vector("bounds.min"),
                bounds.required("max")
                    .vector("bounds.max"),
            ),
        )
    }

    private fun extractMotions(gltf: JsonNode): List<ModelMotion> =
        (gltf.get("animations")?.array("animations") ?: emptyList())
            .mapIndexed { index, animation ->
                val intervals = animation.required("samplers")
                    .array("samplers")
                    .map { sampler -> animationTimeRange(gltf, sampler) }
                require(intervals.isNotEmpty()) { "animation has no samplers" }
                val start = intervals.minOf { it.first }
                val end = intervals.maxOf { it.second }
                val name = animationName(animation, index)
                ModelMotion(index, name, animationRole(animation, name), start, end, end - start)
            }

    private fun animationTimeRange(gltf: JsonNode, sampler: JsonNode): Pair<Double, Double> {
        val accessor = gltf.required("accessors")
            .element(sampler.required("input"), "sampler.input")
        require(isSupportedAnimationTimeAccessor(accessor)) { "invalid animation time accessor" }
        val minimum = accessor.required("min")
            .array("min")
        val maximum = accessor.required("max")
            .array("max")
        require(minimum.size == 1 && maximum.size == 1) { "time accessor requires scalar min/max" }
        val start = minimum.single()
            .number("animation.min")
        val end = maximum.single()
            .number("animation.max")
        require(start in 0.0..end) { "invalid animation time range" }
        return start to end
    }

    private fun matchesExpectedCount(actualCount: Int, expectedCount: Int?): Boolean =
        expectedCount == null || actualCount == expectedCount

    private fun matchesEmbeddedPerspectiveCamera(
        embeddedName: String,
        embeddedType: String,
        expectedName: String,
    ): Boolean = embeddedName == expectedName && embeddedType == PERSPECTIVE_CAMERA_TYPE

    private fun isSupportedAnimationTimeAccessor(accessor: JsonNode): Boolean {
        val isScalar = accessor.path("type").asString() == SCALAR_ACCESSOR_TYPE
        val usesFloatComponents = accessor.required("componentType")
            .int("componentType") == FLOAT_COMPONENT_TYPE
        val hasSamples = accessor.required("count")
            .int("count") > 0

        return isScalar && usesFloatComponents && hasSamples
    }

    private fun animationName(animation: JsonNode, index: Int): String =
        animation.get("name")
            ?.takeUnless { it.isNull || it.asString().isEmpty() }
            ?.text("animation.name")
            ?: "animation_$index"

    private fun animationRole(animation: JsonNode, name: String): String =
        animation.path("extras").get("role")
            ?.takeUnless { it.isNull || it.asString().isEmpty() }
            ?.text("animation.role")
            ?: knownMotionRoles[name]
            ?: DEFAULT_MOTION_ROLE

    private fun extractAsset(relativePath: String, file: Path, gltf: JsonNode): ModelAsset = ModelAsset(
        relativePath,
        Files.size(file),
        sha256(file),
        (gltf.get("extensionsRequired")?.array("extensionsRequired") ?: emptyList())
            .map { it.text("extension") }
            .sorted(),
    )

    private fun sha256(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(HASH_BUFFER_SIZE)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) break
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest()
            .joinToString("") { "%02x".format(it) }
    }

    internal fun readGlb(path: Path): JsonNode = RandomAccessFile(path.toFile(), "r").use { file ->
        fun uint(): Long = Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()))
        require(file.length() >= GLB_HEADER_SIZE) { "truncated GLB header" }
        val magic = uint()
        val version = uint()
        val declaredLength = uint()
        val hasValidHeader = magic == GLB_MAGIC &&
            version == GLB_VERSION &&
            declaredLength == file.length()
        require(hasValidHeader) { "invalid GLB header/length" }
        var document: JsonNode? = null
        while (file.filePointer < file.length()) {
            val first = file.filePointer == 12L
            require(file.length() - file.filePointer >= 8) { "truncated chunk header" }
            val size = uint(); val kind = uint()
            require(isValidChunkSize(size, file.length() - file.filePointer)) { "invalid chunk length" }
            if (first) {
                require(isReadableJsonChunk(kind, size)) { "first chunk must be readable JSON" }
                val bytes = ByteArray(size.toInt()); file.readFully(bytes)
                document = ManifestJson.mapper.readTree(bytes)
            } else {
                require(kind != 0x4e4f534aL) { "duplicate JSON chunk" }
                file.seek(file.filePointer + size)
            }
        }
        val parsedDocument = requireNotNull(document) { "missing glTF 2.0 JSON" }
        val documentVersion = parsedDocument.path("asset")
            .path("version")
            .asString()
        require(documentVersion == GLTF_VERSION) { "missing glTF 2.0 JSON" }
        parsedDocument
    }

    private fun isValidChunkSize(size: Long, remainingBytes: Long): Boolean =
        size % 4 == 0L && size <= remainingBytes

    private fun isReadableJsonChunk(kind: Long, size: Long): Boolean =
        kind == JSON_CHUNK_TYPE && size <= Int.MAX_VALUE
}

private fun JsonNode.text(at: String): String {
    require(isNonblankString()) { "$at: must be a nonblank string" }
    return asString()
}
private fun JsonNode.int(at: String): Int {
    require(isJvmInt()) { "$at: must be a JVM Int" }
    return intValue()
}
private fun JsonNode.number(at: String): Double {
    require(isFiniteNumber()) { "$at: must be finite" }
    return doubleValue()
}

private fun JsonNode.isNonblankString(): Boolean = isString && asString().isNotBlank()

private fun JsonNode.isJvmInt(): Boolean = isIntegralNumber && canConvertToInt()

private fun JsonNode.isFiniteNumber(): Boolean = isNumber && doubleValue().isFinite()
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
