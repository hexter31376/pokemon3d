package com.pokemon3d.api.catalog.infrastructure.configuration

import com.pokemon3d.api.assets.domain.ModelAssetStorage
import com.pokemon3d.api.catalog.application.ModelResourceService
import com.pokemon3d.api.catalog.application.PokemonQueryService
import com.pokemon3d.api.catalog.domain.ModelMetadataRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ModelQueryConfiguration {
    @Bean
    fun pokemonQueryService(metadata: ModelMetadataRepository) = PokemonQueryService(metadata)

    @Bean
    fun modelResourceService(
        metadata: ModelMetadataRepository,
        storage: ObjectProvider<ModelAssetStorage>,
    ) = ModelResourceService(metadata, storage.ifAvailable)
}
