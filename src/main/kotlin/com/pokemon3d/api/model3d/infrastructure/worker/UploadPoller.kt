package com.pokemon3d.api.model3d.infrastructure.worker

import com.pokemon3d.api.model3d.application.ModelUploadWorker
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

class UploadPoller(private val worker: ModelUploadWorker, private val batchSize: Int) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = $$"${pokemon.uploads.poll-delay-ms:1000}")
    fun poll() {
        try {
            repeat(batchSize) {
                if (!worker.runOnce()) return
            }
        } catch (ex: Exception) {
            log.error("Upload worker polling failed: {}", ex.javaClass.simpleName)
        }
    }
}
