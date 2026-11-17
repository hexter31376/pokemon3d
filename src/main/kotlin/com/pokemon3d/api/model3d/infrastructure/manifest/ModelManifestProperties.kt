package com.pokemon3d.api.model3d.infrastructure.manifest

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("pokemon.models")
data class ModelManifestProperties(val manifestPath: String = "")

