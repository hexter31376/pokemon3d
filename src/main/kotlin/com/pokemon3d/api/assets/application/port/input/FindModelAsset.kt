package com.pokemon3d.api.assets.application.port.input

import com.pokemon3d.api.assets.domain.ModelAsset
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import java.net.URI

fun interface FindModelAsset {
    /** Null means the asset is absent; inaccessible or inconsistent storage raises an error. */
    fun findUrl(asset: ModelAsset, location: ModelStorageLocation): URI?
}
