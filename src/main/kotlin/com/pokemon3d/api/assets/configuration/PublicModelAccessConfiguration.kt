package com.pokemon3d.api.assets.configuration

import com.pokemon3d.api.assets.adapter.output.s3.PublicModelAccessConfigurer
import com.pokemon3d.api.assets.adapter.output.s3.S3ClientFactory
import com.pokemon3d.api.assets.adapter.output.s3.S3StorageProperties
import com.pokemon3d.api.assets.application.port.input.ConfigureModelAccess
import com.pokemon3d.api.assets.application.service.ModelAccessService

object PublicModelAccessConfiguration {
    fun withAccess(action: (ConfigureModelAccess) -> Unit) {
        val properties = S3StorageProperties.fromEnvironment()
        S3ClientFactory.create(properties).use { client ->
            action(ModelAccessService(PublicModelAccessConfigurer(client, properties)))
        }
    }
}
