package tech.kzen.auto.server.context.runtime.boot

import org.junit.Test
import tech.kzen.auto.server.context.runtime.KzenAutoRuntime
import tech.kzen.auto.server.context.runtime.KzenAutoRuntimeConfig
import tech.kzen.auto.server.context.runtime.PluginBootException
import tech.kzen.auto.server.context.runtime.PluginScopeId
import tech.kzen.auto.server.context.runtime.PluginUniverseBuilder
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue


/** Runs in its own JVM (pluginUniverseTest): explicit initialization, identical re-initialization, conflict. */
class RuntimeInitializationBootTest {
    @Test
    fun `identical initialization is a no-op and a conflicting one names both configurations`() {
        assertFalse(KzenAutoRuntime.isInitialized())
        assertFailsWith<IllegalStateException> { KzenAutoRuntime.current() }

        val root = Files.createTempDirectory("universe")
        PluginUniverseBuilder(root).plugin("alpha") { jar("a.jar") { resource("marker", "1") } }
        val first = KzenAutoRuntime.initialize(KzenAutoRuntimeConfig(root))
        assertTrue(KzenAutoRuntime.isInitialized())
        assertEquals(listOf(PluginScopeId.application, PluginScopeId("alpha")), first.scopes.all.map { it.id })

        // Same root spelled through a relative segment still compares equal after normalization
        val spelledDifferently = root.resolve("..").resolve(root.fileName)
        assertSame(first, KzenAutoRuntime.initialize(KzenAutoRuntimeConfig(spelledDifferently)))
        assertSame(first, KzenAutoRuntime.currentOrDefault())
        assertSame(first, KzenAutoRuntime.current())

        val other = Files.createTempDirectory("universe-other")
        val failure = assertFailsWith<PluginBootException> {
            KzenAutoRuntime.initialize(KzenAutoRuntimeConfig(other))
        }
        assertTrue(failure.message!!.contains(root.toAbsolutePath().normalize().toString()), failure.message)
        assertTrue(failure.message!!.contains(other.toAbsolutePath().normalize().toString()), failure.message)
        assertSame(first, KzenAutoRuntime.current(), "the pinned universe survives a refused re-initialization")
    }
}
