package com.pokemon3d.api

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Path

@SpringBootTest(properties = [
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
])
class Pokemon3dApplicationTests {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun manifestProperties(registry: DynamicPropertyRegistry) {
            registry.add("pokemon.models.manifest-path") {
                Path.of(Pokemon3dApplicationTests::class.java.getResource("/manifest/valid.json")!!.toURI()).toString()
            }
        }
    }


    @Test
    fun contextLoads() {
    }

}
