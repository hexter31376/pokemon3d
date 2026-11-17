package com.pokemon3d.api.model3d.infrastructure.manifest.tools

import com.pokemon3d.api.model3d.infrastructure.manifest.ManifestJson
import com.pokemon3d.api.model3d.infrastructure.manifest.ManifestValidator
import com.pokemon3d.api.model3d.infrastructure.manifest.ModelManifest
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions

internal object ManifestWriter {
    fun writeAtomic(path: Path, manifest: ModelManifest) {
        ManifestValidator.validate(manifest)
        val destination = path.toAbsolutePath()
        Files.createDirectories(destination.parent)
        val posix = Files.getFileAttributeView(destination.parent, PosixFileAttributeView::class.java) != null
        val permissions = if (posix) {
            try { Files.getPosixFilePermissions(destination) } catch (_: NoSuchFileException) {
                PosixFilePermissions.fromString("rw-r--r--")
            }
        } else null
        val temporary = Files.createTempFile(destination.parent, ".manifest-", ".tmp")
        try {
            val bytes = (ManifestJson.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest) + "\n").toByteArray(Charsets.UTF_8)
            FileChannel.open(temporary, WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                if (permissions != null) Files.setPosixFilePermissions(temporary, permissions)
                channel.force(true)
            }
            // Fail rather than silently degrading to a non-atomic replacement.
            Files.move(temporary, destination, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
