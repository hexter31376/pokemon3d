package com.pokemon3d.api.model3d.application

import com.pokemon3d.api.model3d.domain.*
import com.pokemon3d.api.model3d.domain.exception.ModelStorageException
import java.time.Duration

/** S3 work happens between, never inside, the repository's DB transactions. */
class ModelUploadWorker(
    private val jobs: ModelUploadRepository,
    private val publisher: ModelAssetPublisher,
    private val lease: Duration,
    private val maxAttempts: Int,
    private val retryDelay: Duration,
) {
    fun runOnce(): Boolean {
        val job = jobs.claim(lease, maxAttempts) ?: return false
        try {
            publisher.publish(job.model, job.source)
            jobs.complete(job)
        } catch (ex: Exception) {
            // Persist classifications only; SDK messages can include sensitive transport details.
            val reason = (ex as? ModelStorageException)?.failure?.name ?: ex.javaClass.simpleName
            jobs.fail(job, reason, maxAttempts, retryDelay)
        }
        return true
    }
}
