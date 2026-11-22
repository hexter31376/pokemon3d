package com.pokemon3d.api.publishing.application.service

import com.pokemon3d.api.publishing.application.port.input.ManageModelReleases
import com.pokemon3d.api.publishing.application.port.output.ModelReleaseRepository
import com.pokemon3d.api.publishing.domain.ReleaseStatus
import java.util.UUID

class ModelReleaseService(private val releases: ModelReleaseRepository) : ManageModelReleases {
    override fun status(releaseId: UUID): ReleaseStatus = releases.status(releaseId)

    override fun activate(releaseId: UUID): ReleaseStatus {
        releases.activate(releaseId)
        return releases.status(releaseId)
    }

    override fun retry(releaseId: UUID): ReleaseStatus {
        releases.retry(releaseId)
        return releases.status(releaseId)
    }
}
