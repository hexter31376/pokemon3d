package com.pokemon3d.api.global.configuration

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
    info = Info(
        title = "포켓몬 3D API",
        version = "v1",
        description = "포켓몬과 3D 모델 정보를 조회하는 읽기 전용 API입니다.",
    ),
)
class OpenApiConfiguration
