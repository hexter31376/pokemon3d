package com.pokemon3d.api.publishing.configuration

import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.publishing.adapter.output.persistence.LegacyStorageLocationBackfill
import jakarta.persistence.EntityManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "pokemon.storage.s3", name = ["enabled"], havingValue = "true")
class LegacyStorageConfiguration {
    @Bean
    fun legacyStorageLocationBackfill(entityManager: EntityManager, location: ModelStorageLocation) =
        LegacyStorageLocationBackfill(entityManager, location)
}
