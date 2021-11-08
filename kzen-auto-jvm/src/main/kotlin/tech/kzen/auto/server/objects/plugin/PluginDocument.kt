package tech.kzen.auto.server.objects.plugin

import com.google.common.io.Files
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.plugin.model.ReportDefinerDetail
import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.detached.api.DetachedAction
import tech.kzen.auto.common.util.data.FilePath
import tech.kzen.auto.common.util.data.FilePathJvm.toPath
import tech.kzen.auto.plugin.definition.ReportDefiner
import tech.kzen.auto.server.objects.plugin.PluginUtils.asCommon
import tech.kzen.auto.server.objects.report.service.ReportUtils.asCommon
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.yaml.YamlList
import tech.kzen.lib.common.util.yaml.YamlParser
import tech.kzen.lib.common.util.yaml.YamlString
import tech.kzen.lib.platform.ClassName
import java.net.JarURLConnection
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.isRegularFile


@Reflect
class PluginDocument(
    private val jarPath: String,
    private val selfLocation: ObjectLocation
):
    DocumentArchetype(),
    DetachedAction
{
    //-----------------------------------------------------------------------------------------------------------------
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


    fun jarRoot(): URI? {
        val path = parseJarPath()
            ?: return null

        // https://stackoverflow.com/a/2271974/1941359
        val pathString = path.toUri().toString()

        return URI("jar:$pathString!/")
    }


    fun jarClassLoader(): URLClassLoader? {
        val jarRoot = jarRoot()
            ?: return null

        return URLClassLoader(
            "plugin-${selfLocation.documentPath.name}",
            arrayOf(jarRoot.toURL()),
            ClassLoader.getSystemClassLoader())
    }


    fun loadDefiners(classLoader: ClassLoader): List<ReportDefiner<*>>? {
        val jarRoot = jarRoot()
            ?: return null

        val pluginsPathInJar = "${jarRoot}META-INF/kzen/plugins.yaml"

        // https://bugs.openjdk.java.net/browse/JDK-8239054?page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel&showAll=true
        val pluginsInJarUrl = URL(pluginsPathInJar)
        val pluginsInJarConnection = pluginsInJarUrl.openConnection() as JarURLConnection
        pluginsInJarConnection.useCaches = false
        val pluginsYaml = pluginsInJarConnection.inputStream.use { it.readAllBytes() }.toString(Charsets.UTF_8)

        val pluginClasses = (YamlParser.parse(pluginsYaml) as? YamlList)
            ?.values
            ?.map { (it as YamlString).value }
            ?: listOf()

        val builder = mutableListOf<ReportDefiner<*>>()

        for (pluginClass in pluginClasses) {
            val loadedClass = classLoader.loadClass(pluginClass)
            val noArgConstructor = loadedClass.getDeclaredConstructor()
            val newInstance = noArgConstructor.newInstance()

            val cast = newInstance as ReportDefiner<*>
            builder.add(cast)
        }

        return builder
    }


    private fun definerDetailsImpl(classLoader: ClassLoader): List<ReportDefinerDetail>? {
        val processorDefiners = loadDefiners(classLoader)
            ?: return null

        @Suppress("UnnecessaryVariable")
        val definerDetails = processorDefiners.map { processorDefiner ->
            val info = processorDefiner.info()

            val commonCoordinate = info.coordinate.asCommon()
            val commonDataEncodingSpec = info.dataEncoding.asCommon()

            val definition = processorDefiner.define()
            val modelType = ClassName(definition.reportDataDefinition.outputModelType.name)

            ReportDefinerDetail(
                commonCoordinate,
                info.extensions,
                commonDataEncodingSpec,
                info.priority,
                modelType
            )
        }

        return definerDetails
    }


    private fun definerDetails(): List<ReportDefinerDetail>? {
        val classLoader = jarClassLoader()
            ?: return null

        val definerDetails = classLoader.use {
            definerDetailsImpl(it)
        }

        // attempt to nudge the classLoader
        System.gc()

        return definerDetails
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(request: ExecutionRequest): ExecutionResult {
        val definerDetails = definerDetails()
            ?: return ExecutionFailure("Please provide a valid plugin jar path")

        val asCollection = definerDetails.map { it.asCollection() }

        return ExecutionSuccess(
            ExecutionValue.of(asCollection),
            NullExecutionValue)
    }
}