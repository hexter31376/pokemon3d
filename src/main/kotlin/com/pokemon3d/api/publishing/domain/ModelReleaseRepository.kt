package com.pokemon3d.api.publishing.domain

import com.pokemon3d.api.assets.domain.ModelAsset
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import java.util.UUID

/** Release mutations commit atomically with their membership and upload jobs. */
interface ModelReleaseRepository {
    fun register(
        id: UUID,
        requestKey: String,
        version: String,
        fingerprint: String,
        storageLocation: ModelStorageLocation,
        models: List<PreparedModel>,
    ): UUID
    fun registerPatch(
        id: UUID,
        requestKey: String,
        version: String,
        fingerprint: String,
        storageLocation: ModelStorageLocation,
        baseReleaseId: UUID,
        models: List<PreparedModel>,
    ): UUID
    fun findRequest(requestKey: String, fingerprint: String): UUID?
    fun findPublishedAsset(asset: ModelAsset, location: ModelStorageLocation): ModelAsset?
    fun activeReleaseId(): UUID?
    fun status(releaseId: UUID): ReleaseStatus
    fun activate(releaseId: UUID)
    fun retry(releaseId: UUID)
}
