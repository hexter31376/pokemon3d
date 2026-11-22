package com.pokemon3d.api.publishing.configuration

import com.pokemon3d.api.Pokemon3dApplication
import com.pokemon3d.api.assets.configuration.AssetMaintenanceConfiguration
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.publishing.adapter.output.filesystem.FileModelUploadSpool
import com.pokemon3d.api.publishing.adapter.output.manifest.JsonImportManifestReader
import com.pokemon3d.api.publishing.application.port.input.ImportModels
import com.pokemon3d.api.publishing.application.port.input.ManageModelReleases
import com.pokemon3d.api.publishing.application.port.input.PrunePublishedModels
import com.pokemon3d.api.publishing.application.port.output.ModelAssetRetentionStore
import com.pokemon3d.api.publishing.application.port.output.ModelReleaseRepository
import com.pokemon3d.api.publishing.application.port.output.ModelUploadQueue
import com.pokemon3d.api.publishing.application.service.ModelImportService
import com.pokemon3d.api.publishing.application.service.ModelMaintenanceService
import com.pokemon3d.api.publishing.application.service.ModelReleaseService
import java.nio.file.Path
import org.springframework.beans.factory.getBean
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ApplicationContext

class PublishingCliConfiguration(private val context: ApplicationContext) {
    companion object {
        fun withServices(action: (PublishingCliConfiguration) -> Unit) {
            SpringApplicationBuilder(Pokemon3dApplication::class.java)
                .web(WebApplicationType.NONE)
                .run(
                    "--spring.profiles.active=local",
                    "--spring.docker.compose.enabled=false",
                    "--pokemon.uploads.enabled=false",
                    "--pokemon.storage.s3.enabled=false",
                )
                .use { action(PublishingCliConfiguration(it)) }
        }
    }

    fun releases(): ManageModelReleases = ModelReleaseService(context.getBean<ModelReleaseRepository>())

    fun imports(): ImportModels = ModelImportService(
        context.getBean<ModelReleaseRepository>(),
        JsonImportManifestReader(),
        spool(),
        ModelStorageLocation(
            context.environment.getRequiredProperty("pokemon.storage.s3.bucket"),
            context.environment.getProperty("pokemon.storage.s3.prefix", ""),
        ),
    )

    fun maintenance(): PrunePublishedModels {
        val assets = AssetMaintenanceConfiguration { context.environment.getProperty(it) }
        return ModelMaintenanceService(
            context.getBean<ModelAssetRetentionStore>(),
            assets.pruning(),
            context.getBean<ModelUploadQueue>(),
            spool(),
            assets.location,
        )
    }

    private fun spool() = FileModelUploadSpool(
        Path.of(context.environment.getProperty("pokemon.uploads.spool-path", "runtime-data/uploads")),
    )
}
