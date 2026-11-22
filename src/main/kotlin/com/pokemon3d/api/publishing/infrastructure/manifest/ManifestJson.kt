package com.pokemon3d.api.publishing.infrastructure.manifest

import java.nio.file.Files
import java.nio.file.Path
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule

/** Strict parsing and validation for offline extraction and database import. */
internal object ManifestJson {
    val mapper: JsonMapper = JsonMapper.builder()
        .addModule(
            KotlinModule.Builder()
                .enable(KotlinFeature.StrictNullChecks)
                .build(),
        )
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .build()

    fun read(path: Path): ModelManifest =
        Files.newInputStream(path).use { input ->
            mapper.readValue(input, ModelManifest::class.java)
                .also(ManifestValidator::validate)
        }
}
