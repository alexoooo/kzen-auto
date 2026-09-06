package tech.kzen.auto.server.context.runtime.boot

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.objects.document.plugin.model.PluginClassDetail
import tech.kzen.auto.common.objects.document.plugin.model.PluginScopeDetail
import tech.kzen.auto.server.context.KzenAutoConfig
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.context.PluginAvailability
import tech.kzen.auto.server.context.runtime.KzenAutoRuntime
import tech.kzen.auto.server.context.runtime.PluginFixtures
import tech.kzen.auto.server.context.runtime.PluginUniverseBuilder
import tech.kzen.auto.server.context.runtime.kit.KitExpectations
import tech.kzen.auto.server.context.runtime.kit.KitReport
import tech.kzen.auto.server.context.runtime.kit.PluginCompatibilityKit
import tech.kzen.auto.server.objects.plugin.PluginDocument
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.cqrs.CreateDocumentCommand
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.platform.ClassName
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


/**
 * Verify mode pins the universe, so this runs in its own JVM: expected availability, expression identity, and
 * the Plugin document's view read repeatedly from the same pinned state (no new provider instance, equal rows).
 */
class CompatibilityKitBootTest {
    @Test
    fun `verify mode proves availability and expression identity and the document view is a cached projection`() {
        val root = Files.createTempDirectory("kit-verify")
        val universe = PluginUniverseBuilder(root)
        universe.plugin("alpha") {
            jar("alpha.jar") {
                javaClass("fixture.alpha.CountingReader", countingReader())
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
                javaClass("fixture.alpha.LateOne", """
                    package fixture.alpha;
                    @tech.kzen.lib.common.reflect.Reflect
                    public class LateOne {
                        public LateOne(String label, @tech.kzen.lib.common.reflect.Service Repo repo) {}
                    }
                """.trimIndent())
                resource(PluginFixtures.servicesEntry(), "fixture.alpha.CountingReader\n")
                resource(PluginFixtures.moduleReflectionServicesEntry(), "fixture.alpha.AlphaModule\n")
                resource("notation/auto-jvm/alpha/alpha-doc.yaml", "AlphaDoc:\n  is: ScriptStep\n  class: fixture.alpha.AlphaGen\n  label: \"\"\n")
            }
        }
        for (name in listOf("dup-one", "dup-two")) {
            universe.plugin(name) {
                jar("$name.jar") { javaClass("fixture.dup.Same", "package fixture.dup;\npublic class Same {}") }
            }
        }
        val shadowedClass = CompatibilityKitBootTest::class.java
        val shadowBytes = shadowedClass.getResourceAsStream(shadowedClass.simpleName + ".class")!!.readAllBytes()
        universe.plugin("shadow") {
            jar("shadow.jar") { bytes(shadowedClass.name.replace('.', '/') + ".class", shadowBytes) }
        }
        universe.plugin("broken") { jar("broken.jar") { corrupt() } }

        val report = PluginCompatibilityKit.verify(root, KitExpectations(
            loadedScopes = setOf("alpha", "dup-one", "dup-two", "shadow"),
            failedScopes = setOf("broken"),
            readers = setOf("fixture.alpha.counting@1"),
            documents = setOf("auto-jvm/alpha/alpha-doc.yaml"),
            availableClasses = setOf("fixture.alpha.AlphaGen"),
            unavailableClasses = setOf("fixture.alpha.NeedsRepo"),
            ambiguousClasses = setOf("fixture.dup.Same"),
            shadowedClasses = setOf(shadowedClass.name),
            expressionClasses = setOf("fixture.alpha.AlphaGen", "fixture.alpha.Repo")))
        assertTrue(report.ok, report.problems.toString())
        assertEquals(PluginCompatibilityKit.Mode.VERIFY, report.mode)
        assertEquals(mapOf("fixture.alpha.AlphaGen" to KitReport.identical, "fixture.alpha.Repo" to KitReport.identical),
            report.expressionIdentity)
        val alpha = report.scopes.single { it.id == "alpha" }
        assertEquals(PluginClassDetail.available, alpha.classes.single { it.className == "fixture.alpha.AlphaGen" }.availability)
        assertEquals("needs @Service fixture.alpha.Repo", alpha.classes.single { it.className == "fixture.alpha.NeedsRepo" }.detail)
        assertEquals(listOf("fixture.dup.Same"), report.scopes.single { it.id == "dup-two" }.ambiguousClasses)
        assertEquals(listOf(shadowedClass.name), report.scopes.single { it.id == "shadow" }.shadowedClasses)
        assertFalse(report.scopes.single { it.id == "broken" }.loaded)

        // The Plugin document over the same pinned runtime: two reads are equal projections, and the reader
        // provider was constructed exactly as many times as boot (identity) plus contexts (registries) require
        val runtime = KzenAutoRuntime.current()
        val descriptor = runtime.readerDescriptors().single { it.providerClass.name == "fixture.alpha.CountingReader" }
        val constructedField = descriptor.providerClass.getField("constructed")
        val moduleRoot = Files.createTempDirectory("module-view")
        Files.createDirectories(moduleRoot.resolve("src/main/resources/notation/main"))
        val context = KzenAutoContext.create(KzenAutoConfig(
            jsModuleName = "kzen-auto-js", moduleRoot = moduleRoot, workRoot = moduleRoot.resolve("work")))
        try {
            // A notation edit that first names a reflective plugin class teaches this context's view alone
            assertFalse(ClassName("fixture.alpha.LateOne") in context.pluginAvailability.known())
            runBlocking {
                context.graphStore.apply(CreateDocumentCommand(
                    DocumentPath.parse("main/late.yaml"),
                    YamlNotationParser().parseDocumentObjects(
                        "main:\n  is: ScriptStep\n  class: fixture.alpha.LateOne\n  label: \"\"\n")))
            }
            assertEquals(PluginAvailability.Availability.Unavailable(listOf(ClassName("fixture.alpha.Repo"))),
                context.pluginAvailability.known()[ClassName("fixture.alpha.LateOne")])
            context.pluginAvailability.of(ClassName("fixture.alpha.NeedsRepo"))
            val constructedBefore = constructedField.getInt(null)
            val document = PluginDocument(ObjectLocation(DocumentPath.parse("main/plugins.yaml"), ObjectPath.parse("main")),
                runtime, context.pluginAvailability)
            val first = read(document)
            val second = read(document)
            assertEquals(first, second)
            assertEquals(constructedBefore, constructedField.getInt(null), "a read instantiates no provider")
            assertEquals(listOf("application", "alpha", "broken", "dup-one", "dup-two", "shadow"), first.map { it.id })
            assertEquals(report.scopes.single { it.id == "alpha" }.documents, first.single { it.id == "alpha" }.documents)
            assertTrue(first.single { it.id == "alpha" }.classes.any { it.className == "fixture.alpha.NeedsRepo" && it.availability == PluginClassDetail.unavailable })
            assertTrue(first.single { it.id == "alpha" }.classes.any { it.className == "fixture.alpha.LateOne" && it.availability == PluginClassDetail.unavailable })
            assertTrue(first.single { it.id == "application" }.classes.isEmpty(), "kzen own generated classes are not listed: " + first.single { it.id == "application" }.classes)
        }
        finally {
            context.close()
        }
    }


    private fun read(document: PluginDocument): List<PluginScopeDetail> {
        val result = runBlocking { document.execute(ExecutionRequest(RequestParams.empty, null)) } as ExecutionSuccess
        @Suppress("UNCHECKED_CAST")
        return (result.value.get() as List<Map<String, Any?>>).map { PluginScopeDetail.ofCollection(it) }
    }


    private fun countingReader(): String {
        return PluginFixtures.readerCapability("fixture.alpha", "CountingReader", "fixture.alpha", "counting")
            .replace(Regex("public CountingReader\\(\\) \\{\\s*\\}"),
                "public static int constructed;\n    public CountingReader() { constructed++; }")
    }
}
