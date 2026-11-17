package com.pokemon3d.api.model3d.infrastructure.manifest

import com.pokemon3d.api.model3d.domain.ModelAsset
import com.pokemon3d.api.model3d.domain.ModelCamera
import com.pokemon3d.api.model3d.domain.ModelMetadata
import com.pokemon3d.api.model3d.domain.ModelMotion
import kotlin.math.abs
import kotlin.math.sqrt

internal object ManifestValidator {
    private const val VECTOR_DIMENSION = 3
    private const val MIN_HORIZONTAL_DIRECTION_LENGTH = 1e-8
    private const val VECTOR_TOLERANCE = 1e-5
    private const val MOTION_TIME_TOLERANCE = 1e-6

    private val supportedFormKinds = setOf("base", "other", "mega", "regional", "gigantamax", "primal", "eternamax")
    private val supportedGenders = setOf("shared", "genderless", "male", "female")
    private val supportedMotionRoles = setOf("idle", "cry", "physical_attack", "special_attack", "pose", "effect", "other")
    private val modelIdPattern = Regex("[A-Za-z0-9_-]+")
    private val sha256Pattern = Regex("[0-9a-f]{64}")
    private val unsafePathSegments = setOf("", ".", "..")

    fun validate(manifest: ModelManifest) {
        check(manifest.schemaVersion == 1, "schemaVersion", "unsupported version ${manifest.schemaVersion}; expected 1")
        nonblank(manifest.datasetVersion, "datasetVersion")
        check(hasMatchingNonemptyModelCount(manifest), "modelCount", "must match nonempty models")
        val ids = mutableSetOf<String>()
        val paths = mutableSetOf<String>()
        manifest.models.forEachIndexed { index, model -> validateModel(model, index, ids, paths) }
    }

    private fun validateModel(model: ModelMetadata, index: Int, ids: MutableSet<String>, paths: MutableSet<String>) {
        val at = "models[$index](${model.modelId})"
        check(model.modelId.matches(modelIdPattern), "$at.modelId", "invalid ID")
        check(ids.add(model.modelId), "$at.modelId", "duplicate ID")
        check(model.pokedexNumber > 0, "$at.pokedexNumber", "must be positive")
        check(model.generation > 0, "$at.generation", "must be positive")
        nonblank(model.nameKo, "$at.nameKo")
        nonblank(model.nameEn, "$at.nameEn")
        nonblank(model.form.code, "$at.form.code")
        nonblank(model.form.label, "$at.form.label")
        check(model.form.kind in supportedFormKinds, "$at.form.kind", "unsupported value")
        check(model.gender in supportedGenders, "$at.gender", "unsupported value")
        validateAsset(model.asset, "$at.asset", paths)
        validateMotions(model.motions, "$at.motions")
        validateCamera(model.camera, "$at.camera")
    }

    private fun validateAsset(asset: ModelAsset, at: String, paths: MutableSet<String>) {
        check(isSafeRelativeGlbPath(asset.path), "$at.path", "must be a safe relative GLB path")
        check(paths.add(asset.path), "$at.path", "duplicate asset path")
        check(asset.sizeBytes > 0, "$at.sizeBytes", "must be positive")
        check(asset.sha256.matches(sha256Pattern), "$at.sha256", "must be lowercase SHA-256")
        asset.requiredExtensions.forEach { nonblank(it, "$at.requiredExtensions") }
        val hasUniqueExtensions = asset.requiredExtensions.distinct()
            .size == asset.requiredExtensions.size
        check(hasUniqueExtensions, "$at.requiredExtensions", "duplicate extension")
    }

    private fun validateMotions(motions: List<ModelMotion>, at: String) {
        motions.forEachIndexed { index, motion ->
            val path = "$at[$index]"
            check(motion.index == index, "$path.index", "must match animation order")
            nonblank(motion.name, "$path.name")
            check(motion.role in supportedMotionRoles, "$path.role", "unsupported value")
            check(isValidMotionInterval(motion), path, "invalid time range/duration")
        }
    }

    private fun validateCamera(camera: ModelCamera, at: String) {
        nonblank(camera.cameraName, "$at.cameraName")
        vector(camera.direction, "$at.direction")
        vector(camera.target, "$at.target")
        vector(camera.bounds.min, "$at.bounds.min")
        vector(camera.bounds.max, "$at.bounds.max")
        check(isNormalized(camera.direction), "$at.direction", "must be normalized")
        check(isNotParallelToYUp(camera.direction), "$at.direction", "cannot be parallel to Y-up")
        check(isValidFieldOfView(camera.fov), "$at.fov", "must be in (0, 180) degrees")
        check(isValidPadding(camera.padding), "$at.padding", "must be >= 1")
        val min = camera.bounds.min
        val max = camera.bounds.max
        check(isValidBounds(min, max), "$at.bounds", "invalid bounds")
        check(isTargetWithinBounds(camera.target, min, max), "$at.target", "outside bounds")
    }

    private fun isSafeRelativeGlbPath(path: String): Boolean =
        path.endsWith(".glb") &&
            path.split('/').none { it in unsafePathSegments } &&
            path.none { it in "\\:?%#" }

    private fun hasMatchingNonemptyModelCount(manifest: ModelManifest): Boolean =
        manifest.modelCount > 0 && manifest.modelCount == manifest.models.size

    private fun isValidMotionInterval(motion: ModelMotion): Boolean {
        val times = listOf(motion.startTimeSeconds, motion.endTimeSeconds, motion.durationSeconds)
        val durationMatchesBounds = abs(
            motion.durationSeconds - (motion.endTimeSeconds - motion.startTimeSeconds),
        ) <= MOTION_TIME_TOLERANCE

        return times.all { it.isFinite() } &&
            motion.startTimeSeconds >= 0 &&
            motion.endTimeSeconds >= motion.startTimeSeconds &&
            motion.durationSeconds >= 0 &&
            durationMatchesBounds
    }

    private fun isNormalized(direction: List<Double>): Boolean {
        val length = sqrt(direction.sumOf { it * it })
        return abs(length - 1) <= VECTOR_TOLERANCE
    }

    private fun isNotParallelToYUp(direction: List<Double>): Boolean {
        val horizontalLength = sqrt(direction[0] * direction[0] + direction[2] * direction[2])
        return horizontalLength > MIN_HORIZONTAL_DIRECTION_LENGTH
    }

    private fun isValidFieldOfView(fov: Double): Boolean = fov.isFinite() && fov > 0 && fov < 180

    private fun isValidPadding(padding: Double): Boolean = padding.isFinite() && padding >= 1

    private fun isValidBounds(min: List<Double>, max: List<Double>): Boolean {
        val allAxesOrdered = (0..2).all { min[it] <= max[it] }
        val atLeastOneAxisHasSize = (0..2).any { min[it] < max[it] }
        return allAxesOrdered && atLeastOneAxisHasSize
    }

    private fun isTargetWithinBounds(target: List<Double>, min: List<Double>, max: List<Double>): Boolean =
        (0..2).all { axis ->
            target[axis] >= min[axis] - VECTOR_TOLERANCE &&
                target[axis] <= max[axis] + VECTOR_TOLERANCE
        }

    private fun vector(value: List<Double>, at: String) =
        check(containsThreeFiniteNumbers(value), at, "must contain 3 finite numbers")

    private fun containsThreeFiniteNumbers(value: List<Double>): Boolean =
        value.size == VECTOR_DIMENSION && value.all { it.isFinite() }

    private fun nonblank(value: String, at: String) = check(value.isNotBlank(), at, "must not be blank")

    private fun check(condition: Boolean, at: String, message: String) {
        if (!condition) throw IllegalArgumentException("$at: $message")
    }
}
