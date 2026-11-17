package com.pokemon3d.api.model3d.infrastructure.storage.s3

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ObjectKeyResolver(prefix: String) {
    private val prefix = prefix.trim('/')
    init { if (this.prefix.isNotEmpty()) validate(this.prefix) }

    fun resolve(relativePath: String): String {
        validate(relativePath)
        return listOf(prefix, relativePath).filter { it.isNotEmpty() }.joinToString("/")
    }

    private fun validate(path: String) {
        require(path.split('/').none { it.isBlank() || it == "." || it == ".." }
            && path.none { it == '\\' || it == ':' || it == '%' || it.isISOControl() }) { "Invalid relative object path" }
    }
}

class PublicAssetUrlResolver(publicBaseUrl: String) {
    private val base = S3StorageProperties.validateUrl(publicBaseUrl, "public-base-url").toASCIIString().trimEnd('/')
    fun resolve(key: String): URI {
        ObjectKeyResolver("").resolve(key)
        val encoded = key.split('/').joinToString("/") {
            URLEncoder.encode(it, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A")
        }
        return URI("$base/$encoded")
    }
}
