package com.pokemon3d.api.model3d.domain

import java.nio.file.Path
import java.time.Duration
import java.util.UUID

data class PreparedModel(val metadata: ModelMetadata, val source: Path)
data class UploadJob(val id: UUID, val releaseId: UUID, val token: UUID, val model: ModelMetadata, val source: Path)
data class ReleaseStatus(val id: UUID, val datasetVersion: String, val state: String, val expected: Int,
                         val succeeded: Int, val failed: Int, val active: Boolean, val errors: List<String> = emptyList())

/** Durable state transitions; implementations commit each operation in a short transaction. */
interface ModelUploadRepository {
    fun register(id: UUID, requestKey: String, version: String, fingerprint: String, models: List<PreparedModel>): UUID
    fun findRequest(requestKey: String, fingerprint: String): UUID?
    fun claim(lease: Duration, maxAttempts: Int): UploadJob?
    fun complete(job: UploadJob): Boolean
    fun fail(job: UploadJob, reason: String, maxAttempts: Int, retryDelay: Duration)
    fun status(releaseId: UUID): ReleaseStatus
    fun activate(releaseId: UUID)
    fun retry(releaseId: UUID)
}

/** Returns only after remote bytes and metadata have been verified. */
fun interface ModelAssetPublisher {
    fun publish(model: ModelMetadata, source: Path)
}

data class VersionedModel(val datasetVersion: String, val model: ModelMetadata)

class UploadRejectedException(message: String) : RuntimeException(message)
