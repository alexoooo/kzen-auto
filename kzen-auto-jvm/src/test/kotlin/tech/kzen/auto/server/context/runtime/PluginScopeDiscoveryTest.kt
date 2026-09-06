package tech.kzen.auto.server.context.runtime

import org.junit.Test
import tech.kzen.auto.plugin.api.PluginSpiVersion
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * Discovery is pure over the filesystem, so every live case is checked here without touching the
 * process-global runtime (initialization semantics live in the forked `boot` package).
 */
class PluginScopeDiscoveryTest {
    private val loader = PluginScopeDiscoveryTest::class.java.classLoader

    private val greeter = """
        package fixture;
        public class Greeter {
            public String greet(String name) { return "hello " + name; }
        }
    """.trimIndent()


    @Test
    fun `scopes are the application then folders in name order with implicit directory ids`() {
        val root = Files.createTempDirectory("universe")
        val universe = PluginUniverseBuilder(root)
        universe.plugin("zeta") { jar("a.jar") { javaClass("fixture.Greeter", greeter) } }
        universe.plugin("alpha") { jar("z.jar") { resource("marker.txt", "x") }; jar("a.jar") { resource("other.txt", "y") } }
        Files.writeString(root.resolve("not-a-plugin.txt"), "ignored")

        val scopes = PluginScopeDiscovery.discover(root, loader)

        assertEquals(listOf("application", "alpha", "zeta"), scopes.all.map { it.id.value })
        assertTrue(scopes.application.isApplication)
        val alpha = scopes.get(PluginScopeId("alpha"))!!
        assertEquals(listOf("a.jar", "z.jar"), alpha.jars.map { it.fileName.toString() })
        assertEquals(PluginScope.Status.LOADED, alpha.status)
        assertNull(alpha.version)

        val zeta = scopes.get(PluginScopeId("zeta"))!!
        val greeterClass = zeta.requireClassLoader().loadClass("fixture.Greeter")
        assertEquals("hello kzen", greeterClass.getMethod("greet", String::class.java)
            .invoke(greeterClass.getDeclaredConstructor().newInstance(), "kzen"))
        assertFailsWith<ClassNotFoundException> { loader.loadClass("fixture.Greeter") }
    }


    @Test
    fun `manifest supplies id version and spi`() {
        val root = Files.createTempDirectory("universe")
        PluginUniverseBuilder(root).plugin("dir-name") {
            jar("p.jar") { manifest("id: renamed\nversion: 1.2.0\nspi: ${PluginSpiVersion.current}\n") }
        }

        val scopes = PluginScopeDiscovery.discover(root, loader)
        val scope = scopes.get(PluginScopeId("renamed"))
        assertNotNull(scope)
        assertEquals("1.2.0", scope.version)
        assertNull(scopes.get(PluginScopeId("dir-name")))
    }


    @Test
    fun `no plugin root means the application scope alone`() {
        val scopes = PluginScopeDiscovery.discover(null, loader)
        assertEquals(1, scopes.all.size)
        assertTrue(scopes.folders.isEmpty())
    }


    @Test
    fun `a malformed scope fails alone with a named diagnostic`() {
        val root = Files.createTempDirectory("universe")
        val universe = PluginUniverseBuilder(root)
        universe.plugin("broken") { jar("good.jar") { resource("a", "a") }; jar("bad.jar") { corrupt() } }
        universe.plugin("empty") { file("readme.txt", "no jars here") }
        universe.plugin("two-manifests") { jar("a.jar") { manifest("id: x") }; jar("b.jar") { manifest("id: x") } }
        universe.plugin("bad-manifest") { jar("a.jar") { manifest("id: x\nbogus: 1") } }
        universe.plugin("fine") { jar("a.jar") { resource("a", "a") } }

        val scopes = PluginScopeDiscovery.discover(root, loader)

        val broken = scopes.get(PluginScopeId("broken"))!!
        assertEquals(PluginScope.Status.FAILED, broken.status)
        assertTrue(broken.failure!!.contains("unopenable jar bad.jar"), broken.failure)
        assertNull(broken.classLoader)
        assertFailsWith<IllegalStateException> { broken.requireClassLoader() }

        assertTrue(scopes.get(PluginScopeId("empty"))!!.failure!!.contains("no *.jar files"))
        assertTrue(scopes.get(PluginScopeId("two-manifests"))!!.failure!!.contains("2 manifests"))
        assertTrue(scopes.get(PluginScopeId("bad-manifest"))!!.failure!!.contains("unknown keys [bogus]"))
        assertEquals(PluginScope.Status.LOADED, scopes.get(PluginScopeId("fine"))!!.status)
        assertEquals(listOf("fine"), scopes.loadedFolders.map { it.id.value })
    }


    @Test
    fun `duplicate reserved and incompatible ids are boot errors reported together`() {
        val root = Files.createTempDirectory("universe")
        val universe = PluginUniverseBuilder(root)
        universe.plugin("one") { jar("a.jar") { manifest("id: shared") } }
        universe.plugin("two") { jar("a.jar") { manifest("id: shared") } }
        universe.plugin("three") { jar("a.jar") { manifest("id: application") } }
        universe.plugin("four") { jar("a.jar") { manifest("spi: ${PluginSpiVersion.current + 1}") } }

        val failure = assertFailsWith<PluginBootException> { PluginScopeDiscovery.discover(root, loader) }

        assertEquals(3, failure.errors.size, failure.message)
        assertTrue(failure.errors.any { it.contains("Plugin id 'shared' is claimed by 2 scopes") && it.contains("one") && it.contains("two") })
        assertTrue(failure.errors.any { it.contains("reserved id 'application'") })
        assertTrue(failure.errors.any { it.contains("'four'") && it.contains("SPI version ${PluginSpiVersion.current + 1}") })
    }
}
