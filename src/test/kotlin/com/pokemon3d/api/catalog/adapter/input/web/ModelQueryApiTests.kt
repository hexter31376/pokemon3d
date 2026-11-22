package com.pokemon3d.api.catalog.adapter.input.web

import com.pokemon3d.api.assets.application.port.output.ModelAssetStorage
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.assets.domain.StoredModelAsset
import com.pokemon3d.api.publishing.adapter.output.manifest.ManifestJson
import com.pokemon3d.api.publishing.adapter.output.persistence.PublishedModelCatalog
import java.net.URI
import java.nio.file.Path
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:model-query;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "pokemon.uploads.enabled=false",
    "pokemon.storage.s3.enabled=false",
])
@AutoConfigureMockMvc
@Import(ModelQueryApiTests.TestStorageConfiguration::class)
class ModelQueryApiTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var published: PublishedModelCatalog

    @BeforeEach
    fun seedActiveDataset() {
        jdbc.update("UPDATE model_active_release SET release_id = NULL, revision = 0 WHERE singleton = 1")
        jdbc.update("DELETE FROM model_release_member")
        jdbc.update("DELETE FROM model_content")
        jdbc.update("DELETE FROM model_asset")
        jdbc.update("DELETE FROM model_metadata")
        jdbc.update("DELETE FROM model_upload_job")
        jdbc.update("DELETE FROM model_release")

        val fixture = Path.of(requireNotNull(javaClass.getResource("/manifest/valid.json")).toURI())
        val template = ManifestJson.read(fixture).models.single()
        val models = listOf(
            template.copy(
                modelId = "pikachu",
                pokedexNumber = 25,
                nameKo = "피카츄",
                nameEn = "Pikachu",
                shiny = false,
            ),
            template.copy(
                modelId = "pikachu_shiny",
                pokedexNumber = 25,
                nameKo = "피카츄",
                nameEn = "Pikachu",
                shiny = true,
            ),
            template.copy(
                modelId = "charmander",
                pokedexNumber = 4,
                nameKo = "파이리",
                nameEn = "Charmander",
                form = template.form.copy(code = "base", label = "기본"),
            ),
        )
        val releaseId = UUID.randomUUID()
        jdbc.update(
            """INSERT INTO model_release(
                id, request_key, dataset_version, fingerprint, expected_count, base_revision,
                storage_bucket, storage_prefix, state
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'READY')""",
            releaseId,
            "api-test-${UUID.randomUUID()}",
            "api-test-v1",
            "0".repeat(64),
            models.size,
            0,
            "test-bucket",
            "api-test",
        )
        models.forEach { model ->
            val content = published.publish(model, ModelStorageLocation("test-bucket", "api-test"))
            jdbc.update("INSERT INTO model_release_member VALUES (?, ?, ?)", releaseId, model.modelId, content)
        }
        jdbc.update(
            "UPDATE model_active_release SET release_id = ?, revision = 7 WHERE singleton = 1",
            releaseId,
        )
    }

    @Test
    fun `pokemon search groups forms and paginates by pokedex number`() {
        mockMvc.get("/api/v1/pokemon") {
            param("size", "1")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
                jsonPath("$.data.items.length()") { value(1) }
                jsonPath("$.data.items[0].pokedexNumber") { value(4) }
                jsonPath("$.data.totalElements") { value(2) }
                jsonPath("$.data.totalPages") { value(2) }
            }

        mockMvc.get("/api/v1/pokemon") {
            param("size", "1")
            param("page", "1")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.data.items[0].pokedexNumber") { value(25) }
            }
    }

    @Test
    fun `search with no matches returns an empty page and invalid path number returns bad request`() {
        mockMvc.get("/api/v1/models") {
            param("name", "no-such-pokemon")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.data.items.length()") { value(0) }
                jsonPath("$.data.totalElements") { value(0) }
                jsonPath("$.data.totalPages") { value(0) }
            }

        mockMvc.get("/api/v1/pokemon/0")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("COMMON_001") }
            }
    }

    @Test
    fun `model filters search across names and include form shiny gender and generation filters`() {
        mockMvc.get("/api/v1/models") {
            param("name", "PIKA")
            param("shiny", "true")
            param("gender", "shared")
            param("generation", "1")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.data.totalElements") { value(1) }
                jsonPath("$.data.items[0].modelId") { value("pikachu_shiny") }
                jsonPath("$.data.items[0].shiny") { value(true) }
            }
    }

    @Test
    fun `pokemon detail returns all models for one pokedex number`() {
        mockMvc.get("/api/v1/pokemon/25")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.pokemon.modelCount") { value(2) }
                jsonPath("$.data.models.length()") { value(2) }
                jsonPath("$.data.models[0].modelId") { value("pikachu") }
            }
    }

    @Test
    fun `model detail includes public asset and viewer metadata with active revision`() {
        mockMvc.get("/api/v1/models/pikachu")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.modelId") { value("pikachu") }
                jsonPath("$.data.revision") { value(7) }
                jsonPath("$.data.fileUrl") { value("https://assets.example.test/api-test/${templateAssetPath()}") }
                jsonPath("$.data.fileSizeBytes") { value(templateAssetSize()) }
                jsonPath("$.data.camera.cameraName") { value(templateCameraName()) }
                jsonPath("$.data.requiredExtensions") { isArray() }
            }
    }

    @Test
    fun `animation and filter endpoints expose current metadata options`() {
        mockMvc.get("/api/v1/models/pikachu/animations")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.modelId") { value("pikachu") }
                jsonPath("$.data.animations") { isArray() }
            }

        mockMvc.get("/api/v1/filters")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.genders") { isArray() }
                jsonPath("$.data.generations[0]") { value(1) }
                jsonPath("$.data.shinyValues.length()") { value(2) }
            }
    }

    @Test
    fun `missing resources return not found and invalid page size returns bad request`() {
        mockMvc.get("/api/v1/pokemon/999")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("MODEL3D_001") }
            }

        mockMvc.get("/api/v1/models/unknown")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("MODEL3D_001") }
            }

        mockMvc.get("/api/v1/models") {
            param("size", "101")
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("COMMON_001") }
            }
    }

    @Test
    fun `springdoc describes all read only endpoints`() {
        mockMvc.get("/v3/api-docs")
            .andExpect {
                status { isOk() }
                jsonPath("$.info.title") { value("포켓몬 3D API") }
                jsonPath("$.tags[0].name") { value("포켓몬·모델 조회") }
                jsonPath("$.paths['/api/v1/pokemon']") { exists() }
                jsonPath("$.paths['/api/v1/models'].get.summary") { value("모델 검색") }
                jsonPath("$.paths['/api/v1/models/{modelId}']") { exists() }
                jsonPath("$.paths['/api/v1/models/{modelId}/animations']") { exists() }
                jsonPath("$.paths['/api/v1/filters']") { exists() }
            }
    }

    private fun templateAssetPath(): String = "models/fixture_001.glb"

    private fun templateAssetSize(): Long {
        val fixture = Path.of(requireNotNull(javaClass.getResource("/manifest/valid.json")).toURI())
        return ManifestJson.read(fixture).models.single().asset.sizeBytes
    }

    private fun templateCameraName(): String {
        val fixture = Path.of(requireNotNull(javaClass.getResource("/manifest/valid.json")).toURI())
        return ManifestJson.read(fixture).models.single().camera.cameraName
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestStorageConfiguration {
        @Bean
        @Primary
        fun testModelAssetStorage(): ModelAssetStorage = object : ModelAssetStorage {
            override fun find(
                asset: com.pokemon3d.api.assets.domain.ModelAsset,
                storageLocation: ModelStorageLocation,
            ) = StoredModelAsset(asset.sizeBytes, "model/gltf-binary", asset.sha256)

            override fun publicUrl(
                asset: com.pokemon3d.api.assets.domain.ModelAsset,
                storageLocation: ModelStorageLocation,
            ): URI = URI.create("https://assets.example.test/${storageLocation.prefix}/${asset.path}")
        }
    }
}
