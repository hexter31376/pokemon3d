package com.pokemon3d.api.publishing.adapter.output.filesystem

import java.nio.file.Files
import java.nio.file.Path

class CompletedUploadSpool(private val root: Path) {
    fun remove(sources: List<Path>): Int {
        if (!Files.exists(root)) return 0
        val directory = root.toRealPath()
        var removed = 0
        sources.distinct().forEach { source ->
            if (Files.exists(source)) {
                require(source.toRealPath().startsWith(directory) && source.fileName.toString().endsWith(".glb")) {
                    "Completed source is outside the configured GLB spool: $source"
                }
                if (Files.deleteIfExists(source)) removed++
            }
        }
        return removed
    }
}
