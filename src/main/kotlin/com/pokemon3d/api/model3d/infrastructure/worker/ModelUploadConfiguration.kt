package com.pokemon3d.api.model3d.infrastructure.worker

import com.pokemon3d.api.model3d.application.ModelUploadWorker
import com.pokemon3d.api.model3d.domain.*
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.time.Duration

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "pokemon.uploads", name = ["enabled"], havingValue = "true")
class ModelUploadConfiguration {
    @Bean
    fun modelUploadWorker(
        jobs: ModelUploadRepository,
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

class UploadPoller(private val worker: ModelUploadWorker, private val batchSize: Int) {
    private val log = LoggerFactory.getLogger(javaClass)
    @Scheduled(fixedDelayString = $$"${pokemon.uploads.poll-delay-ms:1000}")
    fun poll() {
        try { repeat(batchSize) { if (!worker.runOnce()) return } }
        catch (ex: Exception) { log.error("Upload worker polling failed: {}", ex.javaClass.simpleName) }
    }
}
