package com.pokemon3d.api.assets.infrastructure.storage.s3

import com.pokemon3d.api.assets.domain.ModelAssetPublisher
import com.pokemon3d.api.assets.domain.ModelAssetStorage
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
    fun modelAssetStorage(client: S3Client, properties: S3StorageProperties): ModelAssetStorage =
        S3ModelAssetStorage(client, properties)

    @Bean
    fun modelAssetPublisher(client: S3Client, properties: S3StorageProperties): ModelAssetPublisher =
        S3ModelAssetPublisher(client, properties)
}
