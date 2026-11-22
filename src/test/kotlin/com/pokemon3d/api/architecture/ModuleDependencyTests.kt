package com.pokemon3d.api.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Checks imports and fully qualified references; runtime reflection is intentionally outside this guard. */
class ModuleDependencyTests {
    private val sourceRoot = Path.of("src/main/kotlin/com/pokemon3d/api")
    private val allowedDependencies = mapOf(
        "assets" to setOf("assets"),
        "catalog" to setOf("catalog", "assets", "global"),
        "publishing" to setOf("publishing", "catalog", "assets", "global"),
    )
    private val projectPrefix = "com.pokemon3d.api."
    private val projectReference = Regex("""com\.pokemon3d\.api\.([A-Za-z0-9_.*]+)""")

    @Test
    fun `all modules use the same hexagonal package layout`() {
        productionSources().forEach { source ->
            val expectedPackage = projectPrefix + sourceRoot.relativize(source.path.parent).joinToString(".")
            assertTrue(source.text.lineSequence().any { it == "package $expectedPackage" }, source.path.toString())
            assertTrue(source.layer in setOf("domain", "application", "adapter", "configuration"), source.path.toString())
            if (source.layer == "application") {
                assertTrue(source.relativePackage.split('.')[1] in setOf("port", "service", "model", "exception"), source.path.toString())
            }
            if (source.relativePackage.startsWith("application.port.")) {
                assertTrue(source.relativePackage.split('.')[2] in setOf("input", "output"), source.path.toString())
            }
            if (source.layer == "adapter") {
                assertTrue(source.relativePackage.split('.')[1] in setOf("input", "output"), source.path.toString())
            }
        }
    }

    @Test
    fun `production modules depend only on downstream modules`() = assertReferences { source, target ->
        target.substringBefore('.') in allowedDependencies.getValue(source.module)
    }

    @Test
    fun `core is independent of adapters configuration frameworks and direct IO`() {
        assertReferences { source, target ->
            when (source.layer) {
                "domain" -> target.substringAfter('.').startsWith("domain.")
                "application" -> target.substringAfter('.').let { it.startsWith("domain.") || it.startsWith("application.") }
                else -> true
            }
        }
        val forbidden = listOf("org.springframework.", "jakarta.", "software.amazon.awssdk.",
            "tools.jackson.", "java.sql.", "javax.sql.", "java.nio.file.Files", "java.io.File", "java.net.http.")
        val violations = productionSources().filter { it.layer in setOf("domain", "application") }.flatMap { source ->
            forbidden.filter { it in source.text }.map { "${source.path}: $it" }
        }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun `input adapters call input ports and cannot reach service implementations or output adapters`() =
        assertReferences { source, target ->
            if (!source.relativePackage.startsWith("adapter.input.")) return@assertReferences true
            val relative = target.substringAfter('.')
            when {
                relative.startsWith("application.port.output.") -> false
                relative.startsWith("application.service.") -> false
                relative.startsWith("adapter.output.") -> false
                relative.startsWith("configuration.") -> source.relativePackage == "adapter.input.cli"
                else -> true
            }
        }

    @Test
    fun `ports and output adapters cannot depend on service implementations or input adapters`() =
        assertReferences { source, target ->
            if (!source.relativePackage.startsWith("application.port.") &&
                !source.relativePackage.startsWith("adapter.output.")
            ) return@assertReferences true
            val relative = target.substringAfter('.')
            !relative.startsWith("application.service.") && !relative.startsWith("adapter.input.") &&
                !relative.startsWith("configuration.")
        }

    @Test
    fun `modules expose values and input ports with output adapters allowed to implement downstream ports`() =
        assertReferences { source, target ->
            val targetModule = target.substringBefore('.')
            if (source.module == targetModule || targetModule == "global") return@assertReferences true
            val relative = target.substringAfter('.')
            when {
                relative.startsWith("domain.") -> true
                relative.startsWith("application.model.") -> true
                relative.startsWith("application.exception.") -> true
                relative.startsWith("application.port.input.") -> true
                relative.startsWith("application.port.output.") -> source.relativePackage.startsWith("adapter.output.")
                relative.startsWith("configuration.") -> source.layer == "configuration"
                else -> false
            }
        }

    private fun assertReferences(allowed: (Source, String) -> Boolean) {
        val violations = productionSources().flatMap { source ->
            projectReference.findAll(source.text.lineSequence().filterNot { it.startsWith("package ") }.joinToString("\n"))
                .map { it.groupValues[1] }
                .filterNot { target ->
                    if (target == "Pokemon3dApplication") source.layer == "configuration"
                    else allowed(source, target)
                }
                .map { "${source.path} depends on $it" }.toList()
        }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    private fun productionSources(): List<Source> = Files.walk(sourceRoot).use { paths ->
        val sources = paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .filter { sourceRoot.relativize(it).nameCount > 1 }
            .filter { sourceRoot.relativize(it).getName(0).toString() != "global" }
            .map { path ->
                val relative = sourceRoot.relativize(path.parent)
                Source(path, relative.getName(0).toString(), relative.drop(1).joinToString("."), Files.readString(path))
            }.toList()
        assertEquals(allowedDependencies.keys, sources.map { it.module }.toSet())
        sources
    }

    private data class Source(val path: Path, val module: String, val relativePackage: String, val text: String) {
        val layer: String get() = relativePackage.substringBefore('.')
    }
}
