package tech.kzen.auto.server.context.runtime.boot

import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoConfig
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.context.KzenAutoHost
import tech.kzen.auto.server.context.PluginAvailability
import tech.kzen.auto.server.context.runtime.KzenAutoRuntime
import tech.kzen.auto.server.context.runtime.KzenAutoRuntimeConfig
import tech.kzen.auto.server.context.runtime.PluginFixtures
import tech.kzen.auto.server.context.runtime.PluginUniverseBuilder
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.platform.ClassName
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * Own JVM: context-owned work roots claimed on the runtime (duplicates, aliases and overlaps fail by name,
 * a failed creation leaves no claim, close releases), host services reaching a plugin Worker's `@Service`
 * through the `Class<?>`-keyed builder (the HS02 G5 deferral and the "providing context" half of the
 * availability rule), host/kzen key collisions, and the suppressible logs area.
 */
class WorkRootAndHostBootTest {
    @Test
    fun `work roots host services and logs area`() {
        val universe = PluginUniverseBuilder(Files.createTempDirectory("universe"))
        universe.plugin("five") {
            jar("five.jar") {
                javaClass("fixture.five.Repo", "package fixture.five;\npublic interface Repo { String name(); }")
                javaClass("fixture.five.NeedsRepo", """
                    package fixture.five;
                    @tech.kzen.lib.common.reflect.Reflect
                    public class NeedsRepo {
                        public final Repo repo;
                        public NeedsRepo(@tech.kzen.lib.common.reflect.Service Repo repo) { this.repo = repo; }
                    }
                """.trimIndent())
                javaClass("fixture.five.FiveModule", """
                    package fixture.five;
                    public class FiveModule implements tech.kzen.lib.common.reflect.ModuleReflection {
                        @Override public void register(tech.kzen.lib.common.reflect.ReflectionRegistry registry) {
                            registry.put("fixture.five.NeedsRepo", java.util.List.of("repo"),
                                    java.util.Map.of("repo", "fixture.five.Repo"), args -> new NeedsRepo((Repo) args.get(0)));
                        }
                    }
                """.trimIndent())
                resource(PluginFixtures.moduleReflectionServicesEntry(), "fixture.five.FiveModule\n")
            }
        }
        val runtime = KzenAutoRuntime.initialize(KzenAutoRuntimeConfig(universe.root()))
        val needsRepo = ClassName("fixture.five.NeedsRepo")

        val rootA = Files.createTempDirectory("work-a")
        val rootB = Files.createTempDirectory("work-b")
        val a = context(rootA)
        val b = context(rootB)
        try {
            assertEquals(2, runtime.workRoots.claimedRoots().size)
            assertEquals(rootA.toRealPath(), a.workUtils.base())
            assertTrue(a.workUtils.signature() != b.workUtils.signature())

            // Duplicate, aliased and overlapping roots fail by name while the owner lives
            val duplicate = assertFailsWith<IllegalStateException> { context(rootA) }
            assertTrue(duplicate.message!!.contains("already claimed"), duplicate.message)
            val alias = rootA.resolve("..").resolve(rootA.fileName)
            assertTrue(assertFailsWith<IllegalStateException> { context(alias) }.message!!.contains("already claimed"))
            val nested = assertFailsWith<IllegalStateException> { context(rootA.resolve("nested")) }
            assertTrue(nested.message!!.contains("overlaps"), nested.message)
            assertEquals(2, runtime.workRoots.claimedRoots().size, "refused creations left no claim")

            // A's boot sweep and scratch stay inside A's root; B's marker survives A's creation
            val marker = b.jobWorkPool.scratchBase().resolve("marker")
            Files.createDirectories(marker.parent)
            Files.writeString(marker, "b")
            val c = context(Files.createTempDirectory("work-c"))
            c.close()
            assertTrue(Files.exists(marker))
            assertTrue(a.jobWorkPool.scratchBase().startsWith(rootA.toRealPath()))

            // Without a host service the plugin Worker is unavailable here; a host providing it under the Java
            // interface makes it available and constructible in that context only
            assertEquals(PluginAvailability.Availability.Unavailable(listOf(ClassName("fixture.five.Repo"))),
                a.pluginAvailability.of(needsRepo))
            val repoInterface = Class.forName("fixture.five.Repo", true, ClassLoaderUtils.dynamicParentClassLoader())
            val proxy = Proxy.newProxyInstance(repoInterface.classLoader, arrayOf(repoInterface)) { _, method, _ ->
                if (method.name == "name") "host-repo" else null
            }
            @Suppress("UNCHECKED_CAST")
            val host = KzenAutoHost.builder().service(repoInterface as Class<Any>, proxy).build()
            val providing = context(Files.createTempDirectory("work-host"), host)
            try {
                assertEquals(PluginAvailability.Availability.Available, providing.pluginAvailability.of(needsRepo))
                assertEquals(PluginAvailability.Availability.Unavailable(listOf(ClassName("fixture.five.Repo"))),
                    a.pluginAvailability.of(needsRepo), "the lacking context is untouched")
                val resolved = providing.graphEnvironment.resolve(ClassName("fixture.five.Repo"))
                assertEquals("host-repo", repoInterface.getMethod("name").invoke(resolved))
            }
            finally {
                providing.close()
            }

            // A host key kzen already provides fails creation by name, and the claim is rolled back
            val colliding = KzenAutoHost.builder().service(KzenAutoConfig::class.java, a.config).build()
            val collisionRoot = Files.createTempDirectory("work-collision")
            val collision = assertFailsWith<IllegalStateException> { context(collisionRoot, colliding) }
            assertTrue(collision.message!!.contains("Service already registered"), collision.message)
            assertFalse(runtime.workRoots.isClaimed(collisionRoot.toRealPath()), "a failed creation leaves no claim")
            context(collisionRoot).close()

            // The builder refuses an instance that is not of the key's type
            assertFailsWith<IllegalArgumentException> {
                @Suppress("UNCHECKED_CAST")
                KzenAutoHost.builder().service(repoInterface as Class<Any>, "not a repo")
            }

            // Logs area is presented only when the context manages logs
            assertNotNull(a.managedStorageRegistry.find("logs"))
            val quiet = context(Files.createTempDirectory("work-quiet"), manageLogs = false)
            try {
                assertNull(quiet.managedStorageRegistry.find("logs"))
            }
            finally {
                quiet.close()
            }
        }
        finally {
            a.close()
            b.close()
        }
        assertTrue(a.isWorkRootReleased() && b.isWorkRootReleased())
        assertTrue(runtime.workRoots.claimedRoots().isEmpty())
        context(rootA).close()
    }


    private fun context(root: Path, host: KzenAutoHost = KzenAutoHost.empty, manageLogs: Boolean = true): KzenAutoContext {
        val moduleRoot = Files.createTempDirectory("module")
        Files.createDirectories(moduleRoot.resolve("src/main/resources/notation/main"))
        return KzenAutoContext.create(KzenAutoConfig(
            jsModuleName = "kzen-auto-js", moduleRoot = moduleRoot, workRoot = root, hostServices = host, manageLogs = manageLogs))
    }
}
