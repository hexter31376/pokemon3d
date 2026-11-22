package com.pokemon3d.api.assets.configuration

import com.pokemon3d.api.assets.adapter.output.filesystem.FileAssetSourceReader
import com.pokemon3d.api.assets.adapter.output.s3.S3ClientFactory
import com.pokemon3d.api.assets.adapter.output.s3.S3ModelAssetStorage
import com.pokemon3d.api.assets.adapter.output.s3.S3ModelAssetUploadStore
import com.pokemon3d.api.assets.adapter.output.s3.S3StorageProperties
import com.pokemon3d.api.assets.application.port.input.PublishModelAsset
import com.pokemon3d.api.assets.application.port.output.ModelAssetStorage
import com.pokemon3d.api.assets.application.service.ModelAssetPublicationService
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.services.s3.S3Client

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(S3StorageProperties::class)
@ConditionalOnProperty(prefix = "pokemon.storage.s3", name = ["enabled"], havingValue = "true")
class S3StorageConfiguration {
    @Bean
    fun modelStorageLocation(properties: S3StorageProperties) = ModelStorageLocation(properties.bucket, properties.prefix)

    @Bean(destroyMethod = "close")
    fun modelS3Client(properties: S3StorageProperties): S3Client = S3ClientFactory.create(properties)

    @Bean
    fun modelAssetStorage(client: S3Client, properties: S3StorageProperties): ModelAssetStorage =
        S3ModelAssetStorage(client, properties)

    @Bean
    fun modelAssetPublisher(client: S3Client, properties: S3StorageProperties): PublishModelAsset =
        ModelAssetPublicationService(S3ModelAssetUploadStore(client, properties), FileAssetSourceReader())
}
