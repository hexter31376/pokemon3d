package com.pokemon3d.api.publishing.infrastructure.worker

import com.pokemon3d.api.assets.domain.ModelAssetPublisher
import com.pokemon3d.api.publishing.application.ModelUploadWorker
import com.pokemon3d.api.publishing.domain.ModelUploadQueue
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "pokemon.uploads", name = ["enabled"], havingValue = "true")
class ModelUploadConfiguration {
    @Bean
    fun modelUploadWorker(
        jobs: ModelUploadQueue,
        publisher: ModelAssetPublisher,
        @Value($$"${pokemon.uploads.lease-seconds:600}") lease: Long,
        @Value($$"${pokemon.uploads.max-attempts:3}") attempts: Int,
        @Value($$"${pokemon.uploads.retry-delay-seconds:30}") retry: Long,
    ): ModelUploadWorker {
        require(isValidRetryConfiguration(lease, attempts, retry))
        return ModelUploadWorker(
            jobs,
            publisher,
            Duration.ofSeconds(lease),
            attempts,
            Duration.ofSeconds(retry),
        )
    }

    @Bean
    fun uploadPoller(
        worker: ModelUploadWorker,
        @Value($$"${pokemon.uploads.batch-size:16}") batchSize: Int,
    ): UploadPoller {
        require(batchSize in 1..1000)
        return UploadPoller(worker, batchSize)
    }

    private fun isValidRetryConfiguration(leaseSeconds: Long, attempts: Int, retrySeconds: Long): Boolean =
        leaseSeconds > 0 && attempts > 0 && retrySeconds >= 0
}
