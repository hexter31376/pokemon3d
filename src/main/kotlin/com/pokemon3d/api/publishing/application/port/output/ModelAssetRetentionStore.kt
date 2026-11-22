package com.pokemon3d.api.publishing.application.port.output

import com.pokemon3d.api.assets.domain.ModelAssetRetention
import com.pokemon3d.api.assets.domain.ModelStorageLocation

interface ModelAssetRetentionStore {
    /** Atomically reserves maintenance and makes obsolete assets unavailable for reuse. */
    fun retainCurrentAssets(location: ModelStorageLocation): ModelAssetRetention
    fun finishPruning()
}
