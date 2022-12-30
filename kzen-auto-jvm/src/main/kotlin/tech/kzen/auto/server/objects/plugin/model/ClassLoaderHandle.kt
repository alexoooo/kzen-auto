package tech.kzen.auto.server.objects.plugin.model

import java.net.URLClassLoader


class ClassLoaderHandle(
    val classLoader: ClassLoader,
    private val closers: List<AutoCloseable>
):
    AutoCloseable
{
    companion object {
        fun ofHost(parent: ClassLoader): ClassLoaderHandle {
            val classLoader = object : ClassLoader(parent) {}
            return ClassLoaderHandle(classLoader, listOf())
        }

        fun ofGuest(classLoader: URLClassLoader): ClassLoaderHandle {
            return ClassLoaderHandle(classLoader, listOf(classLoader))
        }

        fun ofChain(classLoader: ClassLoader, chain: List<AutoCloseable>): ClassLoaderHandle {
            return ClassLoaderHandle(classLoader, chain)
        }
    }


    override fun close() {
        closers.forEach {
            it.close()
        }
    }
}