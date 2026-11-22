package com.pokemon3d.api.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Source checks cover imports and fully qualified references without loading Spring or a database. */
class ModuleDependencyTests {
    private val sourceRoot = Path.of("src/main/kotlin/com/pokemon3d/api")
    private val allowedDependencies = mapOf(
        "assets" to setOf("assets"),
        "catalog" to setOf("catalog", "assets", "global"),
        "publishing" to setOf("publishing", "catalog", "assets", "global"),
    )
    private val projectReference = Regex("""com\.pokemon3d\.api\.(\w+)\.(\w+)""")

    @Test
    fun `production modules depend only on downstream modules`() {
        val violations = productionSources().flatMap { path ->
            val module = sourceRoot.relativize(path).getName(0).toString()
            val allowed = allowedDependencies.getValue(module)
            projectReference.findAll(Files.readString(path))
                .filter { it.groupValues[1] !in allowed }
                .map { "$path depends on ${it.value}" }
                .toList()
        }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun `domain and application do not depend on outer layers or frameworks`() {
        val violations = productionSources().flatMap { path ->
            val layer = sourceRoot.relativize(path).getName(1).toString()
            if (layer !in setOf("domain", "application")) return@flatMap emptyList()
            val source = Files.readString(path)
            val allowedLayers = if (layer == "domain") setOf("domain") else setOf("domain", "application")
            val outerReferences = projectReference.findAll(source)
                .filter { it.groupValues[2] !in allowedLayers }
                .map { "$path depends on ${it.value}" }
                .toList()
            val frameworkImports = source.lineSequence().filter {
                it.startsWith("import org.springframework.") ||
                    it.startsWith("import jakarta.persistence.") ||
                    it.startsWith("import software.amazon.awssdk.")
            }.map { "$path: $it" }.toList()
            outerReferences + frameworkImports
        }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun `cross module references use domain or application interfaces outside wiring`() {
        val violations = productionSources().flatMap { path ->
            val relative = sourceRoot.relativize(path)
            val module = relative.getName(0).toString()
            // Infrastructure composes concrete adapters, including the local administrative CLI.
            if (relative.getName(1).toString() == "infrastructure") return@flatMap emptyList()
            projectReference.findAll(Files.readString(path))
                .filter { it.groupValues[1] != module && it.groupValues[1] != "global" }
                .filter { it.groupValues[2] !in setOf("domain", "application") }
                .map { "$path depends on ${it.value}" }
                .toList()
        }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    private fun productionSources(): List<Path> = allowedDependencies.keys.flatMap { module ->
        Files.walk(sourceRoot.resolve(module)).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .toList()
                .also { assertTrue(it.isNotEmpty(), "No production sources found for $module") }
        }
    }
}
