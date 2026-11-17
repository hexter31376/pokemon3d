package com.pokemon3d.api.model3d.domain.exception

enum class StorageFailure {
    OBJECT_NOT_FOUND,
    ACCESS_DENIED,
    UNAVAILABLE,
    BUCKET_NOT_FOUND,
    INTEGRITY_MISMATCH,
    CONFIGURATION_MISMATCH,
}

class ModelStorageException(
    val failure: StorageFailure,
    val objectKey: String?,
    cause: Throwable? = null,
) : RuntimeException("Model storage failure: $failure${objectKey?.let { " ($it)" } ?: ""}", cause)
