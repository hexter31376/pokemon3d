package com.pokemon3d.api.global.dto

import org.springframework.http.HttpStatus

enum class ErrorCode(val status: HttpStatus, val code: String, val message: String) {
    MODEL_NOT_FOUND(HttpStatus.NOT_FOUND, "MODEL3D_001", "모델을 찾을 수 없습니다."),
    MODEL_ASSET_NOT_FOUND(HttpStatus.NOT_FOUND, "MODEL3D_002", "모델 파일을 찾을 수 없습니다."),
    MODEL_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "MODEL3D_003", "모델 저장소를 이용할 수 없습니다."),
    MODEL_STORAGE_INTEGRITY_ERROR(HttpStatus.BAD_GATEWAY, "MODEL3D_004", "모델 파일 정보를 확인할 수 없습니다."),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "COMMON_001", "요청 값이 올바르지 않습니다."),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "COMMON_002", "요청 본문 형식이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_003", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_004", "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_005", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_006", "지원하지 않는 HTTP 메서드입니다."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "COMMON_007", "지원하지 않는 응답 형식입니다."),
    CONFLICT(HttpStatus.CONFLICT, "COMMON_008", "현재 리소스 상태와 충돌하는 요청입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON_009", "지원하지 않는 요청 형식입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_010", "내부 서버 오류가 발생했습니다."),
    REQUEST_FAILED(HttpStatus.BAD_REQUEST, "COMMON_011", "요청을 처리할 수 없습니다.");
}
