package tech.kzen.auto.server.context.runtime.boot

import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoConfig
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.context.runtime.KzenAutoRuntime
import tech.kzen.auto.server.context.runtime.KzenAutoRuntimeConfig
import tech.kzen.auto.server.context.runtime.PluginScope
import tech.kzen.auto.server.context.runtime.PluginScopeId
import tech.kzen.auto.server.context.runtime.PluginUniverseBuilder
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertSame


/**
 * Own JVM: a host pins a universe with a loaded folder and a failed one, then creates several contexts; all
 * share the one runtime, and the failed scope's diagnostic does not stop any of them.
 */
class RuntimeSharedByContextsBootTest {
    @Test
    fun `multiple contexts share the one pinned universe including its failed scope`() {
        val root = Files.createTempDirectory("universe")
        val universe = PluginUniverseBuilder(root)
        universe.plugin("good") { jar("a.jar") { resource("marker", "1") } }
        universe.plugin("broken") { jar("a.jar") { corrupt() } }
        val runtime = KzenAutoRuntime.initialize(KzenAutoRuntimeConfig(root))

        val contexts = (1..3).map {
            val moduleRoot = Files.createTempDirectory("module-$it")
            Files.createDirectories(moduleRoot.resolve("src/main/resources/notation/main"))
            KzenAutoContext.create(KzenAutoConfig(
                jsModuleName = "kzen-auto-js", moduleRoot = moduleRoot, workRoot = moduleRoot.resolve("work")))
        }
        try {
            for (context in contexts) {
                assertSame(runtime, context.runtime)
            }
            assertEquals(PluginScope.Status.LOADED, runtime.scopes.get(PluginScopeId("good"))!!.status)
            assertEquals(PluginScope.Status.FAILED, runtime.scopes.get(PluginScopeId("broken"))!!.status)
            assertEquals(listOf("good"), runtime.scopes.loadedFolders.map { it.id.value })
        }
        finally {
            contexts.forEach { it.close() }
        }
    }
}
