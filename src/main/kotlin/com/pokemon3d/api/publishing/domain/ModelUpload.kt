package com.pokemon3d.api.publishing.domain

import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.catalog.domain.ModelMetadata
import java.nio.file.Path
import java.util.UUID

data class PreparedModel(val metadata: ModelMetadata, val source: Path)
data class UploadJob(
    val id: UUID,
    val releaseId: UUID,
    val token: UUID,
    val model: ModelMetadata,
    val source: Path,
    val storageLocation: ModelStorageLocation,
)
data class ReleaseStatus(
    val id: UUID,
    val datasetVersion: String,
    val state: String,
    val expected: Int,
    val succeeded: Int,
    val failed: Int,
    val active: Boolean,
    val errors: List<String> = emptyList(),
)

class UploadRejectedException(message: String) : RuntimeException(message)
