package tech.kzen.auto.server.objects.plugin

import com.google.common.io.Files
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonTextEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.ProcessorDefinitionDetail
import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.detached.api.DetachedAction
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.auto.common.util.data.FilePath
import tech.kzen.auto.common.util.data.FilePathJvm.toPath
import tech.kzen.auto.plugin.definition.ProcessorDefiner
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.yaml.YamlList
import tech.kzen.lib.common.util.yaml.YamlParser
import tech.kzen.lib.common.util.yaml.YamlString
import tech.kzen.lib.platform.ClassName
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.isRegularFile


@Reflect
class PluginDocument(
    private val jarPath: String
):
    DocumentArchetype(),
    DetachedAction
{
    //-----------------------------------------------------------------------------------------------------------------
    @OptIn(ExperimentalPathApi::class)
    private fun parseJarPath(): Path? {
        val parsedJarPath = FilePath.parse(jarPath)
            ?: return null

        val path = parsedJarPath.toPath()

        @Suppress("UnstableApiUsage")
        val extension = Files.getFileExtension(path.toString())
        if (extension != "jar") {
            return null
        }

        if (! path.isRegularFile()) {
            return null
        }

        return path
    }


    fun loadDefiners(): List<ProcessorDefiner<*>>? {
        val path = parseJarPath()
            ?: return null

        // https://stackoverflow.com/a/2271974/1941359
        val pathString = path.toUri().toString()

        val jarRoot = "jar:$pathString!/"

        val pluginsPathInJar = "${jarRoot}META-INF/kzen/plugins.yaml"
        val pluginsYaml = URL(pluginsPathInJar).readText()

        val pluginClasses = (YamlParser.parse(pluginsYaml) as YamlList)
            .values
            .map { (it as YamlString).value }

        val classLoader = URLClassLoader(arrayOf(URL(jarRoot)))

        val builder = mutableListOf<ProcessorDefiner<*>>()
        for (pluginClass in pluginClasses) {
            val loadedClass = classLoader.loadClass(pluginClass)
            val noArgConstructor = loadedClass.getDeclaredConstructor()
            val newInstance = noArgConstructor.newInstance()

            val cast = newInstance as ProcessorDefiner<*>
            builder.add(cast)
        }

        return builder
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(request: DetachedRequest): ExecutionResult {
        val definers = loadDefiners()
            ?: return ExecutionFailure("Please provide a valid plugin jar path")

        val commonProcessorDefinitionInfoList = definers
            .map { processorDefiner ->
                val info = processorDefiner.info()

                val dataEncodingSpec = CommonDataEncodingSpec(
                    info.dataEncoding.textEncoding?.let { CommonTextEncodingSpec(it.getOrDefault().name()) })

                val definition = processorDefiner.define()
                val modelType = ClassName(definition.processorDataDefinition.outputModelType.name)

                ProcessorDefinitionDetail(
                    info.name,
                    info.extensions,
                    dataEncodingSpec,
                    info.priority,
                    modelType
                )
            }

        val asCollection = commonProcessorDefinitionInfoList.map { it.asCollection() }

        return ExecutionSuccess(
            ExecutionValue.of(asCollection),
            NullExecutionValue)
    }
}