package com.pokemon3d.api.model3d.presentation

import com.pokemon3d.api.global.dto.ApiError
import com.pokemon3d.api.global.dto.ApiResponse
import com.pokemon3d.api.global.dto.ErrorCode
import com.pokemon3d.api.model3d.application.ModelAssetNotFoundException
import com.pokemon3d.api.model3d.application.ModelNotFoundException
import com.pokemon3d.api.model3d.application.ModelResourceNotFoundException
import com.pokemon3d.api.model3d.application.PokemonNotFoundException
import com.pokemon3d.api.model3d.domain.exception.ModelStorageException
import com.pokemon3d.api.model3d.domain.exception.StorageFailure
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
@Order(-1)
class ModelResourceExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(
        ModelNotFoundException::class,
        ModelAssetNotFoundException::class,
        PokemonNotFoundException::class,
    )
    fun notFound(ex: ModelResourceNotFoundException): ResponseEntity<ApiResponse<Nothing>> =
        response(
            when (ex) {
                is ModelAssetNotFoundException -> ErrorCode.MODEL_ASSET_NOT_FOUND
                is ModelNotFoundException -> ErrorCode.MODEL_NOT_FOUND
                is PokemonNotFoundException -> ErrorCode.MODEL_NOT_FOUND
            },
        )

    @ExceptionHandler(ModelStorageException::class)
    fun storageFailure(ex: ModelStorageException): ResponseEntity<ApiResponse<Nothing>> {
        // SDK exception messages can contain transport details; log our classification only.
        log.error("Model storage request failed: reason={}, key={}", ex.failure, ex.objectKey)
        return response(when (ex.failure) {
            StorageFailure.OBJECT_NOT_FOUND -> ErrorCode.MODEL_ASSET_NOT_FOUND
            StorageFailure.INTEGRITY_MISMATCH -> ErrorCode.MODEL_STORAGE_INTEGRITY_ERROR
            else -> ErrorCode.MODEL_STORAGE_UNAVAILABLE
        })
    }

    private fun response(code: ErrorCode) =
        ResponseEntity.status(code.status)
            .body(ApiResponse.failure(ApiError.from(code)))
}
