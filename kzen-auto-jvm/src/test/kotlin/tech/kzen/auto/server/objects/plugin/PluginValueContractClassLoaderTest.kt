package tech.kzen.auto.server.objects.plugin

import tech.kzen.auto.server.objects.plugin.model.ClassLoaderHandle
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.exec.data.value.ValueAccess
import java.net.URLClassLoader
import kotlin.test.Test
import kotlin.test.assertSame


class PluginValueContractClassLoaderTest {
    @Test
    fun guestResolvesValueAccessFromTheExactHostParentIdentity() {
        val host = ClassLoaderUtils.dynamicParentClassLoader()
        URLClassLoader("value-contract-identity-probe", emptyArray(), host).use { guest ->
            ClassLoaderHandle.ofGuest(guest).use { handle ->
                val guestVisible = handle.classLoader.loadClass(ValueAccess::class.java.name)
                assertSame(ValueAccess::class.java, guestVisible)
                assertSame(ValueAccess::class.java.classLoader, guestVisible.classLoader)
            }
        }
    }
}
