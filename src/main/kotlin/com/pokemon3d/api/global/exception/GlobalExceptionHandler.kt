package com.pokemon3d.api.global.exception

import com.pokemon3d.api.global.dto.ApiError
import com.pokemon3d.api.global.dto.ApiResponse
import com.pokemon3d.api.global.dto.ErrorCode
import com.pokemon3d.api.global.dto.FieldErrorDetail
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(ex: BusinessException): ResponseEntity<ApiResponse<Nothing>> {
        if (ex.errorCode.status.is5xxServerError) log.error("Business operation failed", ex)
        return ResponseEntity.status(ex.errorCode.status)
            .body(ApiResponse.failure(ApiError.from(ex.errorCode)))
    }

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        if (statusCode.is5xxServerError) log.error("MVC request failed", ex)
        val fields = if (ex is MethodArgumentNotValidException && statusCode.is4xxClientError) {
            ex.bindingResult.fieldErrors.map {
                FieldErrorDetail(it.field, it.defaultMessage ?: "올바르지 않은 값입니다.")
            } + ex.bindingResult.globalErrors.map {
                FieldErrorDetail(it.objectName, it.defaultMessage ?: "올바르지 않은 요청입니다.")
            }
        } else emptyList()
        val errorCode = when {
            statusCode.is5xxServerError -> ErrorCode.INTERNAL_SERVER_ERROR
            ex is HttpMessageNotReadableException -> ErrorCode.INVALID_REQUEST_BODY
            else -> resolveErrorCode(statusCode)
        }
        // Spring retains protocol headers and checks whether the response is already committed.
        return super.handleExceptionInternal(
            ex, ApiResponse.failure(ApiError.from(errorCode, fields)), headers, statusCode, request,
        )
    }

    private fun resolveErrorCode(statusCode: HttpStatusCode): ErrorCode = when (statusCode.value()) {
        400 -> ErrorCode.INVALID_PARAMETER
        401 -> ErrorCode.UNAUTHORIZED
        403 -> ErrorCode.FORBIDDEN
        404 -> ErrorCode.NOT_FOUND
        405 -> ErrorCode.METHOD_NOT_ALLOWED
        406 -> ErrorCode.NOT_ACCEPTABLE
        409 -> ErrorCode.CONFLICT
        415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE
        in 500..599 -> ErrorCode.INTERNAL_SERVER_ERROR
        else -> ErrorCode.REQUEST_FAILED
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(ex: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unexpected server error", ex)
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.status)
            .body(ApiResponse.failure(ApiError.from(ErrorCode.INTERNAL_SERVER_ERROR)))
    }
}
