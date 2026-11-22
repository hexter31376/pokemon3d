package com.pokemon3d.api.publishing.application.port.output

import com.pokemon3d.api.publishing.domain.UploadJob
import java.nio.file.Path
import java.time.Duration

/** Each operation commits in a short transaction; remote storage work stays outside it. */
interface ModelUploadQueue {
    fun claim(lease: Duration, maxAttempts: Int): UploadJob?
    fun complete(job: UploadJob): Boolean
    fun fail(job: UploadJob, reason: String, maxAttempts: Int, retryDelay: Duration)
    fun completedSourcePaths(): List<Path>
}
