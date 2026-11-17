package com.pokemon3d.api.global.exception

import com.pokemon3d.api.global.dto.ErrorCode

class BusinessException(
    val errorCode: ErrorCode,
    cause: Throwable? = null,
) : RuntimeException(errorCode.message, cause)
