package com.pokemon3d.api.global.dto

@ConsistentCopyVisibility
data class ApiResponse<T> private constructor(
    val success: Boolean,
    val data: T?,
    val error: ApiError?,
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(true, data, null)
        fun success(): ApiResponse<Unit> = ApiResponse(true, null, null)
        fun failure(error: ApiError): ApiResponse<Nothing> = ApiResponse(false, null, error)
    }
}

data class ApiError(
    val code: String,
    val message: String,
    val fields: List<FieldErrorDetail> = emptyList(),
) {
    companion object {
        fun from(code: ErrorCode, fields: List<FieldErrorDetail> = emptyList()): ApiError =
            ApiError(code.code, code.message, fields)
    }
}

data class FieldErrorDetail(val field: String, val message: String)
