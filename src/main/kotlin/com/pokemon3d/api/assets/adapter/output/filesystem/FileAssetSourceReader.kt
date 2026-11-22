package com.pokemon3d.api.assets.adapter.output.filesystem

import com.pokemon3d.api.assets.application.port.output.AssetSourceReader
import com.pokemon3d.api.assets.domain.ContentDigest
import com.pokemon3d.api.assets.domain.digest
import java.nio.file.Files
import java.nio.file.Path

class FileAssetSourceReader : AssetSourceReader {
    override fun contentDigest(source: Path): ContentDigest = Files.newInputStream(source).use(::digest)
}
