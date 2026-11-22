package com.pokemon3d.api.assets.adapter.output.s3

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ObjectKeyResolver(prefix: String) {
    private val prefix = prefix.trim('/')
    init { if (this.prefix.isNotEmpty()) validate(this.prefix) }

    fun resolve(relativePath: String): String {
        validate(relativePath)
        return listOf(prefix, relativePath)
            .filter { it.isNotEmpty() }
            .joinToString("/")
    }

    private fun validate(path: String) {
        val segments = path.split('/')
        require(hasSafeSegments(segments) && hasSafeCharacters(path)) {
            "Invalid relative object path"
        }
    }

    private fun hasSafeSegments(segments: List<String>): Boolean =
        segments.none { it.isBlank() || it == "." || it == ".." }

    private fun hasSafeCharacters(path: String): Boolean =
        path.none { it == '\\' || it == ':' || it == '%' || it.isISOControl() }
}

class PublicAssetUrlResolver(publicBaseUrl: String) {
    private val base = S3StorageProperties.validateUrl(publicBaseUrl, "public-base-url")
        .toASCIIString()
        .trimEnd('/')
    fun resolve(key: String): URI {
        ObjectKeyResolver("")
            .resolve(key)
        val encoded = key.split('/')
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, StandardCharsets.UTF_8)
                    .replace("+", "%20")
                    .replace("*", "%2A")
            }
        return URI("$base/$encoded")
    }
}
