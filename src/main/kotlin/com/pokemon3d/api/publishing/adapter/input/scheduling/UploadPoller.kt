package com.pokemon3d.api.publishing.adapter.input.scheduling

import com.pokemon3d.api.publishing.application.port.input.ProcessModelUpload
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

class UploadPoller(private val worker: ProcessModelUpload, private val batchSize: Int) {
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
