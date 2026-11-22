package com.pokemon3d.api

import com.pokemon3d.api.catalog.domain.ModelMetadataRepository
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:startup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "pokemon.uploads.enabled=false", "pokemon.storage.s3.enabled=false",
])
class Pokemon3dApplicationTests {
    @Autowired lateinit var models: ModelMetadataRepository
    @Test fun `starts with an empty DB and no manifest file`() { assertTrue(models.findAll().isEmpty()) }
}
