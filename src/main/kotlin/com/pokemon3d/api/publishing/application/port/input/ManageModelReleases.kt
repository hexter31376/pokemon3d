package com.pokemon3d.api.publishing.application.port.input

import com.pokemon3d.api.publishing.domain.ReleaseStatus
import java.util.UUID

interface ManageModelReleases {
    fun status(releaseId: UUID): ReleaseStatus
    fun activate(releaseId: UUID): ReleaseStatus
    fun retry(releaseId: UUID): ReleaseStatus
}
