package com.pokemon3d.api.publishing.application.service

import com.pokemon3d.api.assets.application.port.input.PublishModelAsset
import com.pokemon3d.api.assets.domain.exception.ModelStorageException
import com.pokemon3d.api.assets.domain.exception.StorageFailure
import com.pokemon3d.api.publishing.application.port.input.ProcessModelUpload
import com.pokemon3d.api.publishing.application.port.output.ModelUploadQueue
import java.time.Duration

/** S3 work happens between, never inside, the repository's DB transactions. */
class ModelUploadWorker(
    private val jobs: ModelUploadQueue,
    private val publisher: PublishModelAsset,
    private val lease: Duration,
    private val maxAttempts: Int,
    private val retryDelay: Duration,
) : ProcessModelUpload {
    override fun runOnce(): Boolean {
        val job = jobs.claim(lease, maxAttempts) ?: return false
        try {
            publisher.publish(job.model.asset, job.source, job.storageLocation)
            jobs.complete(job)
        } catch (ex: Exception) {
            // Persist classifications only; SDK messages can include sensitive transport details.
            val failure = (ex as? ModelStorageException)?.failure
            val reason = failure?.name ?: ex.javaClass.simpleName
            val retryLimit = if (failure == StorageFailure.CONFIGURATION_MISMATCH) NON_RETRYABLE_ATTEMPT_LIMIT else maxAttempts
            jobs.fail(job, reason, retryLimit, retryDelay)
        }
        return true
    }

    private companion object {
        const val NON_RETRYABLE_ATTEMPT_LIMIT = 1
    }
}
