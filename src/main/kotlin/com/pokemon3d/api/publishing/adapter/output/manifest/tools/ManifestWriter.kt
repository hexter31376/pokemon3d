package com.pokemon3d.api.publishing.adapter.output.manifest.tools

import com.pokemon3d.api.publishing.adapter.output.manifest.ManifestJson
import com.pokemon3d.api.publishing.adapter.output.manifest.ManifestValidator
import com.pokemon3d.api.publishing.application.model.ModelManifest
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

internal object ManifestWriter {
    private const val DEFAULT_MANIFEST_PERMISSIONS = "rw-r--r--"

    fun writeAtomic(path: Path, manifest: ModelManifest) {
        ManifestValidator.validate(manifest)
        val destination = path.toAbsolutePath()
        Files.createDirectories(destination.parent)
        val permissions = destinationPermissions(destination)
        val temporary = Files.createTempFile(destination.parent, ".manifest-", ".tmp")
        try {
            writeTemporary(temporary, manifest, permissions)
            // Fail rather than silently degrading to a non-atomic replacement.
            Files.move(temporary, destination, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun destinationPermissions(destination: Path): Set<PosixFilePermission>? {
        if (Files.getFileAttributeView(destination.parent, PosixFileAttributeView::class.java) == null) return null
        return try {
            Files.getPosixFilePermissions(destination)
        } catch (_: NoSuchFileException) {
            PosixFilePermissions.fromString(DEFAULT_MANIFEST_PERMISSIONS)
        }
    }

    private fun writeTemporary(
        temporary: Path,
        manifest: ModelManifest,
        permissions: Set<PosixFilePermission>?,
    ) {
        val json = ManifestJson.mapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(manifest)
        val bytes = "$json\n".toByteArray(Charsets.UTF_8)
        FileChannel.open(temporary, WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            if (permissions != null) Files.setPosixFilePermissions(temporary, permissions)
            channel.force(true)
        }
    }
}
