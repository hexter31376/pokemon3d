package com.pokemon3d.api.model3d.infrastructure.storage.s3

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DurationStyle
import java.net.URI
import java.time.Duration

// Regular class intentionally avoids a generated toString containing credentials.
@ConfigurationProperties("pokemon.storage.s3")
class S3StorageProperties(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val publicBaseUrl: String = "",
    val region: String = "",
    val bucket: String = "",
    val prefix: String = "",
    val accessKey: String = "",
    val secretKey: String = "",
    val pathStyle: Boolean = true,
    val connectTimeout: Duration = Duration.ofSeconds(3),
    val attemptTimeout: Duration = Duration.ofSeconds(60),
    val requestTimeout: Duration = Duration.ofSeconds(180),
) {
    fun validate() {
        require(enabled) { "Model storage is disabled" }
        validateUrl(endpoint, "endpoint")
        validateUrl(publicBaseUrl, "public-base-url")
        require(region.isNotBlank()) { "pokemon.storage.s3.region is required" }
        require(bucket.matches(Regex("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"))) { "Invalid S3 bucket name" }
        require(accessKey.isNotBlank() && secretKey.isNotBlank()) { "Model storage credentials are required" }
        require(!connectTimeout.isNegative && !connectTimeout.isZero && attemptTimeout >= connectTimeout && requestTimeout >= attemptTimeout) {
            "Require 0 < connect-timeout <= attempt-timeout <= request-timeout"
        }
        ObjectKeyResolver(prefix)
    }

    companion object {
        internal fun validateUrl(value: String, field: String): URI {
            val uri = try { URI(value) } catch (_: Exception) { throw IllegalArgumentException("Invalid storage $field URI") }
            require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
                && uri.query == null && uri.fragment == null && uri.normalize() == uri) { "Invalid storage $field URI" }
            return uri
        }

        fun fromEnvironment(env: Map<String, String> = System.getenv()): S3StorageProperties {
            fun value(name: String) = env["MODEL_STORAGE_$name"].orEmpty()
            fun duration(name: String, fallback: String) = DurationStyle.detectAndParse(value(name).ifBlank { fallback })
            val pathStyle = value("PATH_STYLE").ifBlank { "true" }
            require(pathStyle in setOf("true", "false")) { "MODEL_STORAGE_PATH_STYLE must be true or false" }
            return S3StorageProperties(true, value("ENDPOINT"), value("PUBLIC_BASE_URL"), value("REGION"), value("BUCKET"),
                value("PREFIX"), value("ACCESS_KEY"), value("SECRET_KEY"), pathStyle.toBoolean(),
                duration("CONNECT_TIMEOUT", "3s"), duration("ATTEMPT_TIMEOUT", "60s"), duration("REQUEST_TIMEOUT", "180s"))
        }
    }
}
