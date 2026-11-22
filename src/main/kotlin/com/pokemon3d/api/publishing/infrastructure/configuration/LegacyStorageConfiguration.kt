package com.pokemon3d.api.publishing.infrastructure.configuration

import com.pokemon3d.api.assets.infrastructure.storage.s3.S3StorageProperties
import com.pokemon3d.api.publishing.infrastructure.persistence.LegacyStorageLocationBackfill
import jakarta.persistence.EntityManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "pokemon.storage.s3", name = ["enabled"], havingValue = "true")
class LegacyStorageConfiguration {
    @Bean
    fun legacyStorageLocationBackfill(entityManager: EntityManager, properties: S3StorageProperties) =
        LegacyStorageLocationBackfill(entityManager, properties)
}
