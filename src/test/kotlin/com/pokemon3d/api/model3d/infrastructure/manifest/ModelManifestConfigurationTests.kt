package com.pokemon3d.api.model3d.infrastructure.manifest

import com.pokemon3d.api.model3d.domain.ModelMetadataRepository
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.nio.file.Path
import kotlin.test.*

class ModelManifestConfigurationTests {
    private val runner = ApplicationContextRunner().withUserConfiguration(ModelManifestConfiguration::class.java)

    @Test
    fun `missing configuration fails startup with actionable message`() {
        runner.withPropertyValues("pokemon.models.manifest-path=").run {
            val failure = assertNotNull(it.startupFailure)
            assertContains(generateSequence(failure) { cause -> cause.cause }.last().message!!, "POKEMON_MODELS_MANIFEST_PATH")
        }
    }

    @Test
    fun `invalid external path fails startup`() {
        runner.withPropertyValues("pokemon.models.manifest-path=/nonexistent/pokemon-manifest.json").run {
            assertNotNull(it.startupFailure)
        }
    }

    @Test
    fun `binds path and exposes read only repository`() {
        val path = Path.of(javaClass.getResource("/manifest/valid.json")!!.toURI())
        runner.withPropertyValues("pokemon.models.manifest-path=$path").run {
            assertNull(it.startupFailure)
            assertEquals("fixture-v1", it.getBean(ModelMetadataRepository::class.java).datasetVersion)
        }
    }
}
