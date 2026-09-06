package tech.kzen.auto.server.util

import tech.kzen.auto.server.context.runtime.KzenAutoRuntime


object ClassLoaderUtils {
    /**
     * The loader that defines kzen-auto-jvm itself: the application classpath, plugin scope zero, and the parent
     * of every folder plugin loader. (Not the system or thread-context loader: under a Spring Boot nested-jar
     * `LaunchedURLClassLoader` those differ from the loader that actually defined these classes.)
     */
    fun applicationClassLoader(): ClassLoader {
        return ClassLoaderUtils::class.java.classLoader
    }


    /**
     * The loader dynamic code (compiled expressions, reflective mirrors, plugin definers) resolves types
     * through: the runtime's aggregate over the application classpath and every loaded plugin scope,
     * application-first, so a workspace expression can name classes from several plugins with the identity the
     * plugin's own loader gave them.
     */
    fun dynamicParentClassLoader(): ClassLoader {
        return KzenAutoRuntime.currentOrDefault().aggregateClassLoader
    }
}
