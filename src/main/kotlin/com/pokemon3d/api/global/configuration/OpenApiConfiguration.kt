package com.pokemon3d.api.global.configuration

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
    info = Info(
        title = "Pokemon3D API",
        version = "v1",
        description = "Read-only Pokemon and 3D model catalog",
    ),
)
class OpenApiConfiguration
