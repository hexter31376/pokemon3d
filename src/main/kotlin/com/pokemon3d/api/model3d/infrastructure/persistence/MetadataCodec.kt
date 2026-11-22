package com.pokemon3d.api.model3d.infrastructure.persistence

import com.pokemon3d.api.model3d.domain.ModelMetadata
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.security.MessageDigest

internal object MetadataCodec {
    private val mapper = JsonMapper.builder()
        .addModule(
            KotlinModule.Builder()
                .build(),
        )
        .build()
    fun fingerprint(json: String): String = MessageDigest.getInstance("SHA-256")
        .digest(json.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun encode(model: ModelMetadata): String = mapper.writeValueAsString(model)
    fun decode(json: String): ModelMetadata = mapper.readValue(json, ModelMetadata::class.java)
}
