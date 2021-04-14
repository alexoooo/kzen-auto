package tech.kzen.auto.server.objects.plugin.model

import tech.kzen.auto.plugin.definition.ProcessorDefiner
import java.net.URLClassLoader


data class DefinersAndClassLoader(
    val processorDefiners: List<ProcessorDefiner<*>>,
    val classLoader: URLClassLoader
)
