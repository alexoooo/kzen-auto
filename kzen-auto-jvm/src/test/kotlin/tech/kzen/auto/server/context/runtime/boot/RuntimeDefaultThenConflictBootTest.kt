package tech.kzen.auto.server.context.runtime.boot

import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.context.runtime.KzenAutoRuntime
import tech.kzen.auto.server.context.runtime.KzenAutoRuntimeConfig
import tech.kzen.auto.server.context.runtime.PluginBootException
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame


/**
 * Own JVM: a first context created with no explicit initialization pins the default (standalone) universe,
 * after which a host asking for a plugin root is refused — the documented reason `KzenAutoMain` and an
 * embedding host initialize before their first context.
 */
class RuntimeDefaultThenConflictBootTest {
    @Test
    fun `implicit default initialization then a conflicting explicit one fails`() {
        System.clearProperty(KzenAutoRuntimeConfig.pluginRootSystemProperty)
        val context = KzenAutoContext.forTest()
        try {
            assertNull(context.runtime.config.pluginRoot)
            assertEquals(1, context.runtime.scopes.all.size)
            assertSame(context.runtime, KzenAutoRuntime.current())

            val root = Files.createTempDirectory("universe")
            assertFailsWith<PluginBootException> { KzenAutoRuntime.initialize(KzenAutoRuntimeConfig(root)) }
            assertSame(context.runtime, KzenAutoRuntime.initialize(KzenAutoRuntimeConfig.standalone))
        }
        finally {
            context.close()
        }
    }
}
