package tech.kzen.auto.server.data.read

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue


class ProviderNeutralityTest {
    companion object {
        private val forbiddenSymbols = listOf(
            "java.io.File",
            "java.nio.file.",
            "com.amazonaws.",
            "software.amazon.awssdk.",
            "tech.kzen.auto.server.data.content.local.LocalDataContentProvider")
    }


    @Test
    fun productionReadersDoNotReferenceFilesystemOrProviderSdkTypes() {
        val sourceRoot = moduleDirectory()
            .resolve("src/main/kotlin/tech/kzen/auto/server/data/read")
        val violations = mutableListOf<String>()
        Files.walk(sourceRoot).use { paths ->
            paths
                .filter { it.extension == "kt" }
                .forEach { path ->
                    path.readText().lineSequence()
                        .filter { line -> forbiddenSymbols.any(line::contains) }
                        .mapTo(violations) { line -> "${path.name}: $line" }
                }
        }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }


    private fun moduleDirectory(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        if (workingDirectory.fileName.toString() == "kzen-auto-jvm") {
            return workingDirectory
        }
        val module = workingDirectory.resolve("kzen-auto-jvm")
        check(Files.isDirectory(module)) { "kzen-auto-jvm directory not found from $workingDirectory" }
        return module
    }
}
