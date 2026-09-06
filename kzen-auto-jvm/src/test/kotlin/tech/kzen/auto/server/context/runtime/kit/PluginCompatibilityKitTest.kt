package tech.kzen.auto.server.context.runtime.kit

import org.junit.Test
import tech.kzen.auto.common.objects.document.plugin.model.PluginClassDetail
import tech.kzen.auto.server.context.runtime.PluginFixtures
import tech.kzen.auto.server.context.runtime.PluginUniverseBuilder
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue


/**
 * The kit's inspect mode over tiny constructed universes, in the ordinary suite: it pins nothing, so a healthy
 * universe and a boot-error one can be checked side by side.
 */
class PluginCompatibilityKitTest {
    @Test
    fun `inspect reports scopes contributions service needs shadowing and ambiguity without pinning`() {
        val root = Files.createTempDirectory("kit")
        val universe = PluginUniverseBuilder(root)
        universe.plugin("alpha") {
            jar("alpha.jar") {
                manifest("id: alpha\nversion: 2.1\nspi: 1\n")
                javaClass("fixture.alpha.AlphaReader",
                    PluginFixtures.readerCapability("fixture.alpha", "AlphaReader", "fixture.alpha", "alpha-reader"))
                javaClass("fixture.alpha.AlphaGen", PluginFixtures.reflectClass("fixture.alpha", "AlphaGen"))
                javaClass("fixture.alpha.AlphaModule",
                    PluginFixtures.moduleReflection("fixture.alpha", "AlphaModule", "AlphaGen"))
                javaClass("fixture.alpha.Repo", "package fixture.alpha;\npublic interface Repo {}")
                javaClass("fixture.alpha.NeedsRepo", """
                    package fixture.alpha;
                    @tech.kzen.lib.common.reflect.Reflect
                    public class NeedsRepo {
                        public NeedsRepo(@tech.kzen.lib.common.reflect.Service Repo repo) {}
                    }
                """.trimIndent())
                javaClass("fixture.alpha.NotReflect", "package fixture.alpha;\npublic class NotReflect {}")
                resource(PluginFixtures.servicesEntry(), "fixture.alpha.AlphaReader\n")
                resource(PluginFixtures.moduleReflectionServicesEntry(), "fixture.alpha.AlphaModule\n")
                resource("notation/auto-jvm/alpha/alpha-doc.yaml", "AlphaDoc:\n  is: ScriptStep\n  class: fixture.alpha.AlphaGen\n  label: \"\"\n")
            }
        }
        for (name in listOf("dup-one", "dup-two")) {
            universe.plugin(name) {
                jar("$name.jar") { javaClass("fixture.dup.Same", "package fixture.dup;\npublic class Same {}") }
            }
        }
        val shadowedClass = PluginCompatibilityKitTest::class.java
        val shadowBytes = shadowedClass.getResourceAsStream(shadowedClass.simpleName + ".class")!!.readAllBytes()
        universe.plugin("shadow") {
            jar("shadow.jar") { bytes(shadowedClass.name.replace('.', '/') + ".class", shadowBytes) }
        }
        universe.plugin("broken") { jar("broken.jar") { corrupt() } }

        val report = PluginCompatibilityKit.inspect(root, KitExpectations(
            loadedScopes = setOf("alpha", "dup-one", "dup-two", "shadow"),
            failedScopes = setOf("broken"),
            readers = setOf("fixture.alpha.alpha-reader@1"),
            documents = setOf("auto-jvm/alpha/alpha-doc.yaml"),
            availableClasses = setOf("fixture.alpha.AlphaGen"),
            unavailableClasses = setOf("fixture.alpha.NeedsRepo"),
            ambiguousClasses = setOf("fixture.dup.Same"),
            shadowedClasses = setOf(shadowedClass.name)))

        assertTrue(report.ok, report.problems.toString())
        assertEquals(PluginCompatibilityKit.Mode.INSPECT, report.mode)
        assertEquals(listOf("application", "alpha", "broken", "dup-one", "dup-two", "shadow"), report.scopes.map { it.id })

        val alpha = report.scopes.single { it.id == "alpha" }
        assertEquals("2.1", alpha.version)
        assertEquals("1", alpha.spiVersion)
        assertEquals(listOf("alpha.jar"), alpha.jars)
        assertEquals(listOf("fixture.alpha.AlphaModule"), alpha.generatedModules)
        assertTrue(alpha.documents.single().origin.endsWith("alpha.jar!/notation/auto-jvm/alpha/alpha-doc.yaml"), alpha.documents.toString())
        val needsRepo = alpha.classes.single { it.className == "fixture.alpha.NeedsRepo" }
        assertEquals(PluginClassDetail.resolved, needsRepo.availability)
        assertEquals("needs @Service fixture.alpha.Repo", needsRepo.detail)
        assertEquals(null, alpha.classes.single { it.className == "fixture.alpha.AlphaGen" }.detail)

        val broken = report.scopes.single { it.id == "broken" }
        assertFalse(broken.loaded)
        assertTrue(broken.failure!!.contains("broken.jar"), broken.failure)
        assertEquals(listOf("fixture.dup.Same"), report.scopes.single { it.id == "dup-one" }.ambiguousClasses)
        assertEquals(listOf(shadowedClass.name), report.scopes.single { it.id == "shadow" }.shadowedClasses)

        // Unmet expectations are named, not thrown
        val unmet = PluginCompatibilityKit.inspect(root, KitExpectations(
            loadedScopes = setOf("broken", "missing"),
            readers = setOf("fixture.alpha.other@1"),
            availableClasses = setOf("fixture.alpha.NotReflect"),
            expressionClasses = setOf("fixture.alpha.AlphaGen")))
        assertEquals(5, unmet.problems.size, unmet.problems.toString())
        assertTrue(unmet.problems.any { it.startsWith("scope 'broken' failed to load") })
        assertTrue(unmet.problems.any { it == "scope 'missing' not found" })
        assertTrue(unmet.problems.any { it.startsWith("reader 'fixture.alpha.other@1' not discovered") })
        assertTrue(unmet.problems.any { it.contains("NotReflect") && it.contains("missing @Reflect") })
        assertTrue(unmet.problems.any { it.contains("verify mode") })
        assertTrue(unmet.toMarkdown().contains("**5 problem(s)**"))
    }


    @Test
    fun `a boot error universe is reported and can be expected`() {
        val root = Files.createTempDirectory("kit-boot")
        val universe = PluginUniverseBuilder(root)
        universe.plugin("first") { jar("a.jar") { manifest("id: same\n") } }
        universe.plugin("second") { jar("b.jar") { manifest("id: same\n") } }
        universe.plugin("future") { jar("c.jar") { manifest("spi: 99\n") } }

        val unexpected = PluginCompatibilityKit.inspect(root)
        assertFalse(unexpected.ok)
        assertTrue(unexpected.bootErrors.size >= 2, unexpected.bootErrors.toString())
        assertTrue(unexpected.problems.all { it.startsWith("unexpected boot error") })
        assertTrue(unexpected.scopes.isEmpty())

        val expected = PluginCompatibilityKit.inspect(root, KitExpectations(bootErrors = listOf("same", "SPI")))
        assertTrue(expected.ok, expected.problems.toString())
        assertFalse(PluginCompatibilityKit.inspect(root, KitExpectations(bootErrors = listOf("nothing like this"))).ok)
    }

    @Test
    fun commandLineFlagsAreTheExpectations() {
        val parsed = PluginCompatibilityKit.expectations(arrayOf(
            "root", "--verify",
            "--expect-scope=alpha", "--expect-scope=beta",
            "--expect-failed-scope=broken",
            "--expect-boot-error=duplicate",
            "--expect-reader=a.b@1",
            "--expect-document=auto-jvm/x.yaml",
            "--expect-class=fixture.Alpha",
            "--expect-unavailable-class=fixture.NeedsRepo",
            "--expect-ambiguous-class=fixture.Same",
            "--expect-shadowed-class=fixture.Shadow",
            "--expect-expression=fixture.Alpha"))
        assertEquals(KitExpectations(
            loadedScopes = setOf("alpha", "beta"),
            failedScopes = setOf("broken"),
            bootErrors = listOf("duplicate"),
            readers = setOf("a.b@1"),
            documents = setOf("auto-jvm/x.yaml"),
            availableClasses = setOf("fixture.Alpha"),
            unavailableClasses = setOf("fixture.NeedsRepo"),
            ambiguousClasses = setOf("fixture.Same"),
            shadowedClasses = setOf("fixture.Shadow"),
            expressionClasses = setOf("fixture.Alpha")), parsed)
        assertEquals(KitExpectations(), PluginCompatibilityKit.expectations(arrayOf("root")))
        assertFailsWith<IllegalArgumentException> { PluginCompatibilityKit.expectations(arrayOf("root", "--expect-class=")) }
        assertFailsWith<IllegalArgumentException> { PluginCompatibilityKit.expectations(arrayOf("root", "--verbose")) }
    }
}
