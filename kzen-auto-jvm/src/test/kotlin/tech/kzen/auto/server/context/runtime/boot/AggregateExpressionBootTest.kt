package tech.kzen.auto.server.context.runtime.boot

import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoConfig
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.context.PluginAvailability
import tech.kzen.auto.server.context.runtime.KzenAutoRuntime
import tech.kzen.auto.server.context.runtime.KzenAutoRuntimeConfig
import tech.kzen.auto.server.context.runtime.PluginFixtures
import tech.kzen.auto.server.context.runtime.PluginScopeId
import tech.kzen.auto.server.context.runtime.PluginUniverseBuilder
import tech.kzen.auto.server.service.compile.KotlinCode
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.reflect.GlobalMirror
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.server.reflect.AmbiguousClassException
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue


/**
 * G9 in its own JVM: one expression names classes from two plugin folders and accepts a mirror-created
 * instance in an identity-sensitive cast; a name two folders define fails lazily by name; an application/folder
 * collision resolves to the application copy everywhere and is listed as shadowed; the compiled expression
 * keeps working after a second context; each context's availability view names missing services.
 */
class AggregateExpressionBootTest {
    private val shadowedClass = PluginScopeId::class.java


    @Test
    fun `expressions mirrors and availability over a two-folder universe`() {
        val root = Files.createTempDirectory("universe")
        val universe = PluginUniverseBuilder(root)
        universe.plugin("one") {
            jar("one.jar") {
                javaClass("fixture.one.Quote", """
                    package fixture.one;
                    @tech.kzen.lib.common.reflect.Reflect
                    public class Quote {
                        private final String symbol;
                        public Quote(String symbol) { this.symbol = symbol; }
                        public String symbol() { return symbol; }
                    }
                """.trimIndent())
            }
        }
        universe.plugin("two") {
            jar("two.jar") {
                javaClass("fixture.two.Sizer", "package fixture.two;\npublic class Sizer { public static String tag() { return \"two\"; } }")
            }
        }
        for (name in listOf("three", "four")) {
            universe.plugin(name) {
                jar("$name.jar") { javaClass("fixture.dup.Same", "package fixture.dup;\npublic class Same { public String from() { return \"$name\"; } }") }
            }
        }
        universe.plugin("five") {
            jar("five.jar") {
                javaClass("fixture.five.Repo", "package fixture.five;\npublic interface Repo {}")
                javaClass("fixture.five.NeedsRepo", """
                    package fixture.five;
                    @tech.kzen.lib.common.reflect.Reflect
                    public class NeedsRepo {
                        public NeedsRepo(@tech.kzen.lib.common.reflect.Service Repo repo) {}
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
        universe.plugin("six") {
            jar("six.jar") {
                javaClass("fixture.six.Svc", "package fixture.six;\npublic interface Svc {}")
                javaClass("fixture.six.LateReflective", """
                    package fixture.six;
                    @tech.kzen.lib.common.reflect.Reflect
                    public class LateReflective {
                        public LateReflective(String label, @tech.kzen.lib.common.reflect.Service Svc svc) {}
                    }
                """.trimIndent())
            }
        }
        // The same class bytes the test classpath already serves, staged in a folder plugin: a collision
        val shadowBytes = shadowedClass.getResourceAsStream(shadowedClass.simpleName + ".class")!!.readAllBytes()
        universe.plugin("shadow") {
            jar("shadow.jar") { bytes(shadowedClass.name.replace('.', '/') + ".class", shadowBytes) }
        }

        val runtime = KzenAutoRuntime.initialize(KzenAutoRuntimeConfig(root))
        val contextA = newContext("a")
        try {
            // One expression over two folders, an identity-sensitive cast of a mirror-created instance
            val code = KotlinCode("Probe",
                "class Probe { fun probe(q: Any): String = (q as fixture.one.Quote).symbol() + \":\" + fixture.two.Sizer.tag() }")
            val loader = ClassLoaderUtils.dynamicParentClassLoader()
            assertNull(contextA.cachedKotlinCompiler.tryCompile(code, loader))
            val probeClass = contextA.cachedKotlinCompiler.tryLoad(code, loader)!!
            val quote = GlobalMirror.create(ClassName("fixture.one.Quote"), listOf("AAPL"))
            val probe = probeClass.getDeclaredConstructor().newInstance()
            assertEquals("AAPL:two", probeClass.getMethod("probe", Any::class.java).invoke(probe, quote))
            assertSame(runtime.scopes.get(PluginScopeId("one"))!!.requireClassLoader().loadClass("fixture.one.Quote"),
                quote.javaClass)

            // A name two folders define: lazy ambiguity, recorded on both scopes, served by the mirror as a failure
            val ambiguity = assertFailsWith<AmbiguousClassException> { loader.loadClass("fixture.dup.Same") }
            assertEquals(setOf("three", "four"), ambiguity.definingScopes.toSet())
            assertEquals(listOf("fixture.dup.Same"), runtime.diagnostics.ambiguousClasses(PluginScopeId("three")))
            assertEquals(listOf("fixture.dup.Same"), runtime.diagnostics.ambiguousClasses(PluginScopeId("four")))
            assertTrue(GlobalMirror.contains(ClassName("fixture.dup.Same")))
            val mirrorFailure = assertFailsWith<IllegalArgumentException> {
                GlobalMirror.constructorArgumentNames(ClassName("fixture.dup.Same"))
            }
            assertTrue(mirrorFailure.message!!.contains("2 plugin scopes"), mirrorFailure.message)

            // Application/folder collision: the application copy through the aggregate and the folder's own loader
            assertSame(shadowedClass, loader.loadClass(shadowedClass.name))
            assertSame(shadowedClass, runtime.scopes.get(PluginScopeId("shadow"))!!.requireClassLoader().loadClass(shadowedClass.name))
            assertEquals(listOf(shadowedClass.name), runtime.diagnostics.shadowedClasses(PluginScopeId("shadow")))

            // Availability is per context and learns lazily
            val needsRepo = ClassName("fixture.five.NeedsRepo")
            assertEquals(PluginAvailability.Availability.Unavailable(listOf(ClassName("fixture.five.Repo"))),
                contextA.pluginAvailability.of(needsRepo))
            assertEquals(listOf(needsRepo), contextA.pluginAvailability.contributedBy(PluginScopeId("five")))
            assertTrue(contextA.pluginAvailability.known().containsKey(needsRepo), "checked at creation")
            val late = ClassName("fixture.six.LateReflective")
            assertTrue(!contextA.pluginAvailability.known().containsKey(late), "a reflective class is unknown until first named")
            assertEquals(PluginAvailability.Availability.Unavailable(listOf(ClassName("fixture.six.Svc"))),
                contextA.pluginAvailability.of(late))
            assertTrue(contextA.pluginAvailability.known().containsKey(late))
            assertEquals(PluginAvailability.Availability.Available, contextA.pluginAvailability.of(ClassName("fixture.one.Quote")))
            assertTrue(contextA.pluginAvailability.of(ClassName("fixture.none.Missing")) is PluginAvailability.Availability.Unresolvable)

            // The compiled expression survives a second context: the aggregate is process-global
            val contextB = newContext("b")
            try {
                assertSame(runtime, contextB.runtime)
                assertEquals("AAPL:two", probeClass.getMethod("probe", Any::class.java).invoke(probe, quote))
                assertNull(contextB.cachedKotlinCompiler.tryCompile(code, loader))
                assertEquals(PluginAvailability.Availability.Unavailable(listOf(ClassName("fixture.five.Repo"))),
                    contextB.pluginAvailability.of(needsRepo))
            }
            finally {
                contextB.close()
            }
        }
        finally {
            contextA.close()
        }
    }


    private fun newContext(name: String): KzenAutoContext {
        val moduleRoot = Files.createTempDirectory("module-$name")
        Files.createDirectories(moduleRoot.resolve("src/main/resources/notation/main"))
        return KzenAutoContext.create(KzenAutoConfig(
            jsModuleName = "kzen-auto-js", moduleRoot = moduleRoot, workRoot = moduleRoot.resolve("work")))
    }
}
