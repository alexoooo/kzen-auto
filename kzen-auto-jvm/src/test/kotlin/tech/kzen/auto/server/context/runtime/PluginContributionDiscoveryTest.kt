package tech.kzen.auto.server.context.runtime

import org.junit.Test
import tech.kzen.auto.server.data.read.TestServiceReaderCapability
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.platform.ClassName
import java.nio.file.Files
import java.util.jar.JarFile
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * G10 and the provider rules over a constructed universe, without initializing the process-global runtime:
 * exact-origin notation, application notation discovered once, parent providers counted once, duplicate reader
 * identities and duplicate notation paths as boot errors, a throwing provider retained as its scope's failure.
 */
class PluginContributionDiscoveryTest {
    private val appLoader = PluginContributionDiscoveryTest::class.java.classLoader
    private val alphaDoc = "AlphaThing:\n  is: ScriptStep\n  class: fixture.alpha.AlphaWorker\n"


    @Test
    fun `folders contribute their own notation readers and generated registry with exact origins`() {
        val root = Files.createTempDirectory("universe")
        val universe = PluginUniverseBuilder(root)
        universe.plugin("alpha") {
            jar("alpha.jar") {
                javaClass("fixture.alpha.AlphaReader",
                    PluginFixtures.readerCapability("fixture.alpha", "AlphaReader", "fixture.alpha", "alpha-reader"))
                javaClass("fixture.alpha.AlphaGen", PluginFixtures.reflectClass("fixture.alpha", "AlphaGen"))
                javaClass("fixture.alpha.AlphaModule",
                    PluginFixtures.moduleReflection("fixture.alpha", "AlphaModule", "AlphaGen"))
                resource(PluginFixtures.servicesEntry(), "fixture.alpha.AlphaReader\n")
                resource(PluginFixtures.moduleReflectionServicesEntry(), "fixture.alpha.AlphaModule\n")
                resource("notation/auto-jvm/alpha/alpha-doc.yaml", alphaDoc)
            }
        }
        universe.plugin("beta") { jar("beta.jar") { resource("notation/auto-jvm/beta/beta-doc.yaml", "Beta: {}\n") } }

        val scopes = PluginScopeDiscovery.discover(root, appLoader)
        val contributions = PluginContributionDiscovery.discover(scopes)
        val application = contributions.first()
        val alpha = contributions.single { it.scopeId == PluginScopeId("alpha") }
        val beta = contributions.single { it.scopeId == PluginScopeId("beta") }

        // Application notation is discovered exactly once, by the application scope
        val jobDocument = DocumentPath.parse("auto-jvm/job/job-jvm.yaml")
        assertTrue(application.notationOrigins.containsKey(jobDocument))
        assertNull(alpha.notationOrigins[jobDocument])
        assertNull(beta.notationOrigins[jobDocument])
        assertEquals(setOf("auto-jvm/alpha/alpha-doc.yaml"), alpha.notationOrigins.keys.map { it.asString() }.toSet())

        // A folder document reads back byte-identical to its jar entry, from the folder's own origin
        val alphaPath = DocumentPath.parse("auto-jvm/alpha/alpha-doc.yaml")
        assertTrue(alpha.notationOrigins[alphaPath]!!.endsWith("alpha.jar!/notation/auto-jvm/alpha/alpha-doc.yaml"))
        val entryBytes = JarFile(root.resolve("alpha/alpha.jar").toFile()).use { jar ->
            jar.getInputStream(jar.getJarEntry("notation/auto-jvm/alpha/alpha-doc.yaml")).readAllBytes()
        }
        val readBody = kotlinx.coroutines.runBlocking { alpha.notation!!.readDocument(alphaPath) }
        assertEquals(entryBytes.decodeToString(), readBody)

        // The test classpath's own provider counts once, for the application scope, never for a folder
        assertTrue(application.readers.any { it.identity == TestServiceReaderCapability.serviceIdentity })
        assertTrue(alpha.readers.none { it.identity == TestServiceReaderCapability.serviceIdentity })
        assertTrue(beta.readers.isEmpty())
        val alphaReader = alpha.readers.single()
        assertEquals("fixture.alpha.AlphaReader", alphaReader.providerClass.name)
        assertEquals("alpha-reader", alphaReader.identity.name)
        val one = alphaReader.instantiate()
        val two = alphaReader.instantiate()
        assertTrue(one !== two, "each instantiation is a fresh instance")

        // Generated registrations stay in the scope's own registry
        assertNotNull(alpha.generatedRegistry)
        assertTrue(alpha.generatedRegistry!!.contains(ClassName("fixture.alpha.AlphaGen")))
        assertEquals(listOf("fixture.alpha.AlphaModule"), alpha.moduleReflectionClasses)
        assertNull(beta.generatedRegistry)
        assertTrue(alpha.failures.isEmpty() && beta.failures.isEmpty())
    }


    @Test
    fun `a folder shipping an application document path is a boot error naming both origins`() {
        val root = Files.createTempDirectory("universe")
        PluginUniverseBuilder(root).plugin("shadow") {
            jar("shadow.jar") { resource("notation/auto-jvm/job/job-jvm.yaml", "Job: {}\n") }
        }
        val failure = assertFailsWith<PluginBootException> {
            PluginContributionDiscovery.discover(PluginScopeDiscovery.discover(root, appLoader))
        }
        val error = failure.errors.single()
        assertTrue(error.contains("auto-jvm/job/job-jvm.yaml"), error)
        assertTrue(error.contains("scope 'application' (application classpath)"), error)
        assertTrue(error.contains("scope 'shadow'") && error.contains("shadow.jar"), error)
    }


    @Test
    fun `duplicate reader identities across folders are a boot error`() {
        val root = Files.createTempDirectory("universe")
        val universe = PluginUniverseBuilder(root)
        for (name in listOf("one", "two")) {
            universe.plugin(name) {
                jar("$name.jar") {
                    javaClass("fixture.$name.Reader",
                        PluginFixtures.readerCapability("fixture.$name", "Reader", "fixture", "same-reader"))
                    resource(PluginFixtures.servicesEntry(), "fixture.$name.Reader\n")
                }
            }
        }
        val failure = assertFailsWith<PluginBootException> {
            PluginContributionDiscovery.discover(PluginScopeDiscovery.discover(root, appLoader))
        }
        val error = failure.errors.single()
        assertTrue(error.contains("same-reader") && error.contains("scope 'one'") && error.contains("scope 'two'"), error)
    }


    @Test
    fun `a throwing provider is a named failure on its scope only`() {
        val root = Files.createTempDirectory("universe")
        val universe = PluginUniverseBuilder(root)
        universe.plugin("bad") {
            jar("bad.jar") {
                javaClass("fixture.bad.Reader",
                    PluginFixtures.readerCapability("fixture.bad", "Reader", "fixture", "bad-reader", throwing = true))
                resource(PluginFixtures.servicesEntry(), "fixture.bad.Reader\n")
            }
        }
        universe.plugin("good") {
            jar("good.jar") {
                javaClass("fixture.good.Reader",
                    PluginFixtures.readerCapability("fixture.good", "Reader", "fixture", "good-reader"))
                resource(PluginFixtures.servicesEntry(), "fixture.good.Reader\n")
            }
        }
        val contributions = PluginContributionDiscovery.discover(PluginScopeDiscovery.discover(root, appLoader))
        val bad = contributions.single { it.scopeId == PluginScopeId("bad") }
        val good = contributions.single { it.scopeId == PluginScopeId("good") }
        assertTrue(bad.readers.isEmpty())
        assertTrue(bad.failures.single().contains("fixture.bad.Reader") && bad.failures.single().contains("cannot be constructed"))
        assertEquals("good-reader", good.readers.single().identity.name)
    }
}
