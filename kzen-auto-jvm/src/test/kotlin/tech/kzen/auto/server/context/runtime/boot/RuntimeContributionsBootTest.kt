package tech.kzen.auto.server.context.runtime.boot

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.server.context.KzenAutoConfig
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.context.runtime.KzenAutoRuntime
import tech.kzen.auto.server.context.runtime.KzenAutoRuntimeConfig
import tech.kzen.auto.server.context.runtime.PluginFixtures
import tech.kzen.auto.server.context.runtime.PluginUniverseBuilder
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.reflect.GlobalMirror
import tech.kzen.lib.platform.ClassName
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue


/**
 * Own JVM: a pinned universe with one folder plugin reaches two contexts — each gets its own reader capability
 * instance from the runtime's descriptor, both see the folder's notation beside kzen's own, and the folder's
 * generated registration is served through the global mirror ahead of the reflective fallback.
 */
class RuntimeContributionsBootTest {
    @Test
    fun `two contexts share descriptors but hold distinct instances`() {
        val root = Files.createTempDirectory("universe")
        PluginUniverseBuilder(root).plugin("alpha") {
            jar("alpha.jar") {
                javaClass("fixture.alpha.AlphaReader",
                    PluginFixtures.readerCapability("fixture.alpha", "AlphaReader", "fixture.alpha", "alpha-reader"))
                javaClass("fixture.alpha.AlphaGen", PluginFixtures.reflectClass("fixture.alpha", "AlphaGen"))
                javaClass("fixture.alpha.AlphaModule",
                    PluginFixtures.moduleReflection("fixture.alpha", "AlphaModule", "AlphaGen"))
                resource(PluginFixtures.servicesEntry(), "fixture.alpha.AlphaReader\n")
                resource(PluginFixtures.moduleReflectionServicesEntry(), "fixture.alpha.AlphaModule\n")
                resource("notation/auto-jvm/alpha/alpha-doc.yaml", "AlphaDoc:\n  is: ScriptStep\n  class: fixture.alpha.AlphaGen\n  label: \"\"\n")
            }
        }
        KzenAutoRuntime.initialize(KzenAutoRuntimeConfig(root))

        val contexts = (1..2).map {
            val moduleRoot = Files.createTempDirectory("module-$it")
            Files.createDirectories(moduleRoot.resolve("src/main/resources/notation/main"))
            KzenAutoContext.create(KzenAutoConfig(
                jsModuleName = "kzen-auto-js", moduleRoot = moduleRoot, workRoot = moduleRoot.resolve("work")))
        }
        try {
            val identity = ReaderCapabilityIdentity("fixture.alpha", "alpha-reader", "1")
            val first = contexts[0].readerCapabilityRegistry.resolve(identity)
            val second = contexts[1].readerCapabilityRegistry.resolve(identity)
            assertNotSame(first, second)
            assertEquals("fixture.alpha.AlphaReader", first.javaClass.name)

            for (context in contexts) {
                val paths = runBlocking { context.notationMedia.scan() }.documents.map.keys.map { it.asString() }
                assertTrue("auto-jvm/alpha/alpha-doc.yaml" in paths, paths.toString())
                assertTrue("auto-jvm/job/job-jvm.yaml" in paths)
                val body = runBlocking { context.notationMedia.readDocument(DocumentPath.parse("auto-jvm/alpha/alpha-doc.yaml")) }
                assertTrue(body.startsWith("AlphaDoc:"))
            }

            val generated = ClassName("fixture.alpha.AlphaGen")
            assertTrue(GlobalMirror.contains(generated))
            assertEquals(listOf("label"), GlobalMirror.constructorArgumentNames(generated))
            val created = GlobalMirror.create(generated, listOf("hello"))
            assertEquals("hello", created.javaClass.getMethod("label").invoke(created))
        }
        finally {
            contexts.forEach { it.close() }
        }
    }
}
