package com.pokemon3d.api.assets.application.port.output

import com.pokemon3d.api.assets.domain.ContentDigest
import java.nio.file.Path

fun interface AssetSourceReader {
    fun contentDigest(source: Path): ContentDigest
}
