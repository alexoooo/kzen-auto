package tech.kzen.auto.server.context.runtime.boot

import org.junit.Test
import tech.kzen.auto.server.context.runtime.KzenAutoRuntime
import tech.kzen.auto.server.context.runtime.PluginUniverseBuilder
import tech.kzen.auto.server.context.runtime.kit.KitExpectations
import tech.kzen.auto.server.context.runtime.kit.PluginCompatibilityKit
import java.nio.file.Files
import kotlin.test.assertFalse
import kotlin.test.assertTrue


/** A boot-error universe in verify mode: its own JVM, since a successful initialize would pin it. */
class KitVerifyBootErrorBootTest {
    @Test
    fun `verify reports the boot errors and leaves the runtime unpinned`() {
        val root = Files.createTempDirectory("kit-verify-boot")
        val universe = PluginUniverseBuilder(root)
        universe.plugin("first") { jar("a.jar") { manifest("id: same\n") } }
        universe.plugin("second") { jar("b.jar") { manifest("id: same\n") } }

        val report = PluginCompatibilityKit.verify(root, KitExpectations(bootErrors = listOf("same")))
        assertTrue(report.ok, report.problems.toString())
        assertTrue(report.bootErrors.single().contains("same"), report.bootErrors.toString())
        assertFalse(KzenAutoRuntime.isInitialized(), "a failed initialize pins nothing")
        assertTrue(report.toMarkdown().contains("## Boot errors"))
    }
}
