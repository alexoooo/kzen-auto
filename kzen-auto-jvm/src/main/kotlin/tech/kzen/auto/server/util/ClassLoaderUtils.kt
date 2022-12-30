package tech.kzen.auto.server.util


object ClassLoaderUtils {
    fun dynamicParentClassLoader(): ClassLoader {
        // NB: doesn't work with Spring Boot LaunchedURLClassLoader?
//        return ClassLoader.getSystemClassLoader()
//        return Thread.currentThread().contextClassLoader
//        return KzenAutoMain::class.java.classLoader
        return ClassLoaderUtils::class.java.classLoader
    }
}