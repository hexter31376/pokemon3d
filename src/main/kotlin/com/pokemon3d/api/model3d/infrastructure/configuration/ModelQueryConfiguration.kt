package com.pokemon3d.api.model3d.infrastructure.configuration

import com.pokemon3d.api.model3d.application.ModelResourceService
import com.pokemon3d.api.model3d.application.PokemonQueryService
import com.pokemon3d.api.model3d.domain.ModelAssetStorage
import com.pokemon3d.api.model3d.domain.ModelMetadataRepository
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
