package com.pokemon3d.api.model3d.infrastructure.manifest

import com.pokemon3d.api.model3d.domain.ModelCamera
import kotlin.math.abs
import kotlin.math.sqrt

internal object ManifestValidator {
    fun validate(manifest: ModelManifest) {
        check(manifest.schemaVersion == 1, "schemaVersion", "unsupported version ${manifest.schemaVersion}; expected 1")
        nonblank(manifest.datasetVersion, "datasetVersion")
        check(manifest.modelCount > 0 && manifest.modelCount == manifest.models.size, "modelCount", "must match nonempty models")
        val ids = mutableSetOf<String>()
        val paths = mutableSetOf<String>()
        manifest.models.forEachIndexed { i, model ->
            val at = "models[$i](${model.modelId})"
            check(model.modelId.matches(Regex("[A-Za-z0-9_-]+")), "$at.modelId", "invalid ID")
            check(ids.add(model.modelId), "$at.modelId", "duplicate ID")
            check(model.pokedexNumber > 0, "$at.pokedexNumber", "must be positive")
            check(model.generation > 0, "$at.generation", "must be positive")
            nonblank(model.nameKo, "$at.nameKo")
            nonblank(model.nameEn, "$at.nameEn")
            nonblank(model.form.code, "$at.form.code")
            nonblank(model.form.label, "$at.form.label")
            check(model.form.kind in setOf("base", "other", "mega", "regional", "gigantamax", "primal", "eternamax"), "$at.form.kind", "unsupported value")
            check(model.gender in setOf("shared", "genderless", "male", "female"), "$at.gender", "unsupported value")
            val asset = model.asset
            check(asset.path.endsWith(".glb") && asset.path.split('/').none { it in setOf("", ".", "..") }
                && asset.path.none { it in "\\:?%#" }, "$at.asset.path", "must be a safe relative GLB path")
            check(paths.add(asset.path), "$at.asset.path", "duplicate asset path")
            check(asset.sizeBytes > 0, "$at.asset.sizeBytes", "must be positive")
            check(asset.sha256.matches(Regex("[0-9a-f]{64}")), "$at.asset.sha256", "must be lowercase SHA-256")
            asset.requiredExtensions.forEach { nonblank(it, "$at.asset.requiredExtensions") }
            check(asset.requiredExtensions.distinct().size == asset.requiredExtensions.size, "$at.asset.requiredExtensions", "duplicate extension")
            model.motions.forEachIndexed { index, motion ->
                val path = "$at.motions[$index]"
                check(motion.index == index, "$path.index", "must match animation order")
                nonblank(motion.name, "$path.name")
                check(motion.role in setOf("idle", "cry", "physical_attack", "special_attack", "pose", "effect", "other"), "$path.role", "unsupported value")
                check(listOf(motion.startTimeSeconds, motion.endTimeSeconds, motion.durationSeconds).all { it.isFinite() }
                    && motion.startTimeSeconds >= 0 && motion.endTimeSeconds >= motion.startTimeSeconds
                    && motion.durationSeconds >= 0
                    && abs(motion.durationSeconds - (motion.endTimeSeconds - motion.startTimeSeconds)) <= 1e-6,
                    path, "invalid time range/duration")
            }
            camera(model.camera, "$at.camera")
        }
    }

    private fun camera(camera: ModelCamera, at: String) {
        nonblank(camera.cameraName, "$at.cameraName")
        vector(camera.direction, "$at.direction")
        vector(camera.target, "$at.target")
        vector(camera.bounds.min, "$at.bounds.min")
        vector(camera.bounds.max, "$at.bounds.max")
        check(abs(sqrt(camera.direction.sumOf { it * it }) - 1) <= 1e-5, "$at.direction", "must be normalized")
        check(sqrt(camera.direction[0] * camera.direction[0] + camera.direction[2] * camera.direction[2]) > 1e-8,
            "$at.direction", "cannot be parallel to Y-up")
        check(camera.fov.isFinite() && camera.fov > 0 && camera.fov < 180, "$at.fov", "must be in (0, 180) degrees")
        check(camera.padding.isFinite() && camera.padding >= 1, "$at.padding", "must be >= 1")
        val min = camera.bounds.min
        val max = camera.bounds.max
        check((0..2).all { min[it] <= max[it] } && (0..2).any { min[it] < max[it] }, "$at.bounds", "invalid bounds")
        check((0..2).all { camera.target[it] >= min[it] - 1e-5 && camera.target[it] <= max[it] + 1e-5 }, "$at.target", "outside bounds")
    }

    private fun vector(value: List<Double>, at: String) =
        check(value.size == 3 && value.all { it.isFinite() }, at, "must contain 3 finite numbers")

    private fun nonblank(value: String, at: String) = check(value.isNotBlank(), at, "must not be blank")

    private fun check(condition: Boolean, at: String, message: String) {
        if (!condition) throw IllegalArgumentException("$at: $message")
    }
}
