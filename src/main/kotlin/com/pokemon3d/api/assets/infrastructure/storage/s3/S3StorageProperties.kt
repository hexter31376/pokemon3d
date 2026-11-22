package com.pokemon3d.api.assets.infrastructure.storage.s3

import java.net.URI
import java.net.URISyntaxException
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DurationStyle

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
        require(BUCKET_NAME_PATTERN.matches(bucket)) { "Invalid S3 bucket name" }
        require(hasCredentials()) { "Model storage credentials are required" }
        require(hasValidTimeoutConfiguration()) {
            "Require 0 < connect-timeout <= attempt-timeout <= request-timeout"
        }
        ObjectKeyResolver(prefix)
    }

    private fun hasCredentials(): Boolean = accessKey.isNotBlank() && secretKey.isNotBlank()

    private fun hasValidTimeoutConfiguration(): Boolean =
        connectTimeout.isPositiveDuration() &&
            attemptTimeout >= connectTimeout &&
            requestTimeout >= attemptTimeout

    companion object {
        private const val DEFAULT_PATH_STYLE = "true"
        private val HTTP_SCHEMES = setOf("http", "https")
        private val BUCKET_NAME_PATTERN = Regex("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")

        internal fun validateUrl(value: String, field: String): URI {
            val uri = try {
                URI(value)
            } catch (ex: URISyntaxException) {
                throw IllegalArgumentException("Invalid storage $field URI", ex)
            }
            require(isValidStorageUrl(uri)) { "Invalid storage $field URI" }
            return uri
        }

        private fun isValidStorageUrl(uri: URI): Boolean =
            uri.scheme in HTTP_SCHEMES &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null &&
                uri.normalize() == uri

        fun fromEnvironment(env: Map<String, String> = System.getenv()): S3StorageProperties =
            fromProperties(env::get)

        fun fromProperties(property: (String) -> String?): S3StorageProperties {
            fun value(name: String): String = property("MODEL_STORAGE_$name").orEmpty()
            fun duration(name: String, fallback: String): Duration =
                DurationStyle.detectAndParse(
                    value(name).ifBlank { fallback },
                )

            val pathStyle = value("PATH_STYLE")
                .ifBlank { DEFAULT_PATH_STYLE }
            require(pathStyle == "true" || pathStyle == "false") {
                "MODEL_STORAGE_PATH_STYLE must be true or false"
            }
            return S3StorageProperties(
                enabled = true,
                endpoint = value("ENDPOINT"),
                publicBaseUrl = value("PUBLIC_BASE_URL"),
                region = value("REGION"),
                bucket = value("BUCKET"),
                prefix = value("PREFIX"),
                accessKey = value("ACCESS_KEY"),
                secretKey = value("SECRET_KEY"),
                pathStyle = pathStyle.toBoolean(),
                connectTimeout = duration("CONNECT_TIMEOUT", "3s"),
                attemptTimeout = duration("ATTEMPT_TIMEOUT", "60s"),
                requestTimeout = duration("REQUEST_TIMEOUT", "180s"),
            )
        }
    }
}

private fun Duration.isPositiveDuration(): Boolean = !isNegative && !isZero
