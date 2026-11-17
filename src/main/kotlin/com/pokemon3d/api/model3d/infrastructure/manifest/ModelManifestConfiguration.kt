package com.pokemon3d.api.model3d.infrastructure.manifest

import com.pokemon3d.api.model3d.domain.ModelMetadataRepository
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ModelManifestProperties::class)
class ModelManifestConfiguration {
    @Bean
    fun modelMetadataRepository(properties: ModelManifestProperties): ModelMetadataRepository {
        if (properties.manifestPath.isBlank()) {
            throw ManifestLoadException("pokemon.models.manifest-path (POKEMON_MODELS_MANIFEST_PATH) is required")
        }
        return JsonManifestModelMetadataRepository(Path.of(properties.manifestPath))
    }
}
