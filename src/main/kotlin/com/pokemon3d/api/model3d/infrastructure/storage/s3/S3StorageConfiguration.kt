package com.pokemon3d.api.model3d.infrastructure.storage.s3

import com.pokemon3d.api.model3d.application.ModelResourceService
import com.pokemon3d.api.model3d.domain.ModelAssetStorage
import com.pokemon3d.api.model3d.domain.ModelMetadataRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.services.s3.S3Client

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(S3StorageProperties::class)
@ConditionalOnProperty(prefix = "pokemon.storage.s3", name = ["enabled"], havingValue = "true")
class S3StorageConfiguration {
    @Bean(destroyMethod = "close")
    fun modelS3Client(properties: S3StorageProperties): S3Client = S3ClientFactory.create(properties)

    @Bean
    fun modelAssetStorage(client: S3Client, properties: S3StorageProperties): ModelAssetStorage = S3ModelAssetStorage(client, properties)

    @Bean
    fun modelResourceService(metadata: ModelMetadataRepository, storage: ModelAssetStorage) = ModelResourceService(metadata, storage)
}
