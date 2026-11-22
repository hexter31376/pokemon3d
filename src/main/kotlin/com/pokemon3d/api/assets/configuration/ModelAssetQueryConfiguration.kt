package com.pokemon3d.api.assets.configuration

import com.pokemon3d.api.assets.application.port.input.FindModelAsset
import com.pokemon3d.api.assets.application.port.output.ModelAssetStorage
import com.pokemon3d.api.assets.application.service.ModelAssetQueryService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ModelAssetQueryConfiguration {
    @Bean
    fun findModelAsset(storage: ObjectProvider<ModelAssetStorage>): FindModelAsset =
        ModelAssetQueryService(storage.ifAvailable)
}
