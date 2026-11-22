package com.pokemon3d.api.catalog.configuration

import com.pokemon3d.api.assets.application.port.input.FindModelAsset
import com.pokemon3d.api.catalog.application.port.output.ModelMetadataRepository
import com.pokemon3d.api.catalog.application.service.ModelResourceService
import com.pokemon3d.api.catalog.application.service.ModelCatalogQueryService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ModelQueryConfiguration {
    @Bean
    fun pokemonQueryService(metadata: ModelMetadataRepository) = ModelCatalogQueryService(metadata)

    @Bean
    fun modelResourceService(
        metadata: ModelMetadataRepository,
        assets: FindModelAsset,
    ) = ModelResourceService(metadata, assets)
}
