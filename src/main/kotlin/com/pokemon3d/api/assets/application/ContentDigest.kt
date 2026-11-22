package com.pokemon3d.api.assets.application

import java.io.InputStream
import java.security.MessageDigest

private const val DIGEST_BUFFER_SIZE = 1024 * 1024

data class ContentDigest(val sizeBytes: Long, val sha256: String)

fun digest(input: InputStream): ContentDigest {
    val hash = MessageDigest.getInstance("SHA-256")
    val bytes = ByteArray(DIGEST_BUFFER_SIZE)
    var length = 0L
    while (true) {
        val n = input.read(bytes)
        if (n < 0) break
        hash.update(bytes, 0, n)
        length += n
    }
    return ContentDigest(
        length,
        java.util.HexFormat.of()
            .formatHex(hash.digest()),
    )
}
