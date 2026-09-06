package tech.kzen.auto.server.context.runtime

import tech.kzen.auto.plugin.api.PluginSpiVersion
import java.io.IOException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence


/**
 * Boot-time discovery of the plugin universe: the application classpath as scope zero, then one scope per
 * subdirectory of the plugin root in name order, each over its `*.jar` files in name order. Pure over the
 * filesystem — nothing here touches process-global state, which is why the ordinary test suite can exercise
 * every live case without initializing the runtime.
 *
 * Per-scope failures (an unopenable jar, two manifests, a malformed manifest, an empty folder) mark that scope
 * [PluginScope.Status.FAILED] with a named diagnostic and leave the rest intact. Universe-level failures
 * (duplicate or reserved ids, an SPI mismatch) are boot errors, all reported at once as a [PluginBootException].
 */
object PluginScopeDiscovery {
    private const val jarSuffix = ".jar"


    fun discover(pluginRoot: Path?, applicationClassLoader: ClassLoader): PluginScopes {
        val scopes = mutableListOf(PluginScope.application(applicationClassLoader))
        val errors = mutableListOf<String>()

        if (pluginRoot != null) {
            require(pluginRoot.isDirectory()) { "Plugin root is not a directory: $pluginRoot" }
            val directories = Files.list(pluginRoot).use { entries ->
                entries.asSequence().filter { it.isDirectory() }.sortedBy { it.name }.toList()
            }
            for (directory in directories) {
                scopes.add(folderScope(directory, applicationClassLoader, errors))
            }
        }

        val byId = scopes.groupBy { it.id }
        for ((id, sameId) in byId) {
            // A folder claiming the reserved id is already reported above; not also a duplicate
            if (sameId.size > 1 && !id.isApplication()) {
                errors.add("Plugin id '$id' is claimed by ${sameId.size} scopes: " +
                    sameId.joinToString { it.directory?.toString() ?: "the application classpath" })
            }
        }

        if (errors.isNotEmpty()) {
            throw PluginBootException(errors)
        }
        return PluginScopes(scopes)
    }


    private fun folderScope(
        directory: Path,
        parent: ClassLoader,
        bootErrors: MutableList<String>
    ): PluginScope {
        val canonical = try {
            directory.toRealPath()
        }
        catch (e: IOException) {
            directory.toAbsolutePath()
        }
        val implicitId = PluginScopeId(canonical.name)

        val jars = Files.list(directory).use { entries ->
            entries.asSequence()
                .filter { it.isRegularFile() && it.name.endsWith(jarSuffix) }
                .sortedBy { it.name }
                .toList()
        }
        if (jars.isEmpty()) {
            return failed(implicitId, canonical, jars, "no *.jar files in $canonical")
        }

        val manifests = mutableListOf<Pair<Path, String>>()
        for (jar in jars) {
            try {
                JarFile(jar.toFile()).use { jarFile ->
                    val entry = jarFile.getJarEntry(PluginManifest.resourcePath)
                    if (entry != null) {
                        manifests.add(jar to jarFile.getInputStream(entry).use { it.readAllBytes().decodeToString() })
                    }
                }
            }
            catch (e: IOException) {
                return failed(implicitId, canonical, jars, "unopenable jar ${jar.name}: ${e.message}")
            }
        }
        if (manifests.size > 1) {
            return failed(implicitId, canonical, jars,
                "${manifests.size} manifests (${PluginManifest.resourcePath}) in one scope: " +
                    manifests.joinToString { it.first.name })
        }

        val manifest = try {
            manifests.firstOrNull()?.let { PluginManifest.parse(it.second) } ?: PluginManifest.empty
        }
        catch (e: IllegalArgumentException) {
            return failed(implicitId, canonical, jars, "malformed manifest in ${manifests.first().first.name}: ${e.message}")
        }

        val id = manifest.id?.let { PluginScopeId(it) } ?: implicitId
        if (id.isApplication()) {
            bootErrors.add("Plugin at $canonical claims the reserved id '${PluginScopeId.application}'")
        }
        if (manifest.spiVersion != null && manifest.spiVersion != PluginSpiVersion.current) {
            bootErrors.add("Plugin '$id' at $canonical declares plugin SPI version ${manifest.spiVersion}; " +
                "this kzen provides ${PluginSpiVersion.current}")
        }

        val loader = URLClassLoader(
            "plugin-$id",
            jars.map { it.toUri().toURL() }.toTypedArray(),
            parent)
        return PluginScope(id, canonical, jars, manifest, loader, PluginScope.Status.LOADED, null)
    }


    private fun failed(id: PluginScopeId, directory: Path, jars: List<Path>, failure: String): PluginScope {
        return PluginScope(id, directory, jars, PluginManifest.empty, null, PluginScope.Status.FAILED, failure)
    }
}
