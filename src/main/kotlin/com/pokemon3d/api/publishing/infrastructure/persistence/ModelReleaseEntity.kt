package com.pokemon3d.api.publishing.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "model_release")
class ModelReleaseEntity(
    @Id
    var id: UUID,
    @Column(name = "dataset_version", nullable = false, length = 200)
    var datasetVersion: String,
    @Column(name = "storage_bucket", length = 63)
    var storageBucket: String?,
    @Column(name = "storage_prefix", length = 200)
    var storagePrefix: String?,

)

@Entity
@Table(name = "model_active_release")
class ModelActiveReleaseEntity(
    @Id
    @Column(name = "singleton")
    var singleton: Int,
    @Column(name = "revision", nullable = false)
    var revision: Long,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "release_id")
    var release: ModelReleaseEntity?,
)
