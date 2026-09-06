package tech.kzen.auto.server.context.runtime

import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider


/**
 * Assembles a plugin root for tests: each [plugin] is one directory holding one or more jars built from Java
 * sources compiled at test time with the JDK compiler (with `-parameters`, against the test JVM's classpath)
 * plus arbitrary resources. Classes compiled this way exist only inside the plugin jar, never on the test
 * classpath — which is what makes a folder plugin's own class distinguishable from an application one.
 */
class PluginUniverseBuilder(
    private val root: Path
) {
    init {
        Files.createDirectories(root)
    }


    fun plugin(directoryName: String, configure: Plugin.() -> Unit = {}): Path {
        val directory = root.resolve(directoryName)
        Files.createDirectories(directory)
        Plugin(directory).apply(configure).also { it.finish() }
        return directory
    }


    fun root(): Path = root


    class Plugin(val directory: Path) {
        private val jars = mutableListOf<JarBuilder>()

        /** A jar named [fileName] in this plugin's directory. */
        fun jar(fileName: String, configure: JarBuilder.() -> Unit): JarBuilder {
            val jar = JarBuilder(directory.resolve(fileName)).apply(configure)
            jars.add(jar)
            return jar
        }

        /** A file that is not a jar (ignored by discovery). */
        fun file(fileName: String, content: String) {
            Files.writeString(directory.resolve(fileName), content)
        }

        fun finish() {
            jars.forEach { it.write() }
        }
    }


    class JarBuilder(private val path: Path) {
        private val sources = mutableMapOf<String, String>()
        private val resources = mutableMapOf<String, ByteArray>()
        private var corrupt = false

        /** A Java class [qualifiedName] compiled from [source]. */
        fun javaClass(qualifiedName: String, source: String): JarBuilder {
            sources[qualifiedName] = source
            return this
        }

        fun resource(entryName: String, content: String): JarBuilder {
            resources[entryName] = content.toByteArray()
            return this
        }

        /** A verbatim entry, e.g. class bytes copied from the application classpath to stage a collision. */
        fun bytes(entryName: String, content: ByteArray): JarBuilder {
            resources[entryName] = content
            return this
        }

        fun manifest(yaml: String): JarBuilder = resource(PluginManifest.resourcePath, yaml)

        /** Writes bytes that are not a zip at all. */
        fun corrupt(): JarBuilder {
            corrupt = true
            return this
        }

        fun write() {
            if (corrupt) {
                Files.write(path, "not a jar".toByteArray())
                return
            }
            val classes = if (sources.isEmpty()) mapOf() else compile(sources)
            JarOutputStream(Files.newOutputStream(path)).use { out ->
                for ((name, bytes) in classes) {
                    out.putNextEntry(JarEntry(name.replace('.', '/') + ".class"))
                    out.write(bytes)
                    out.closeEntry()
                }
                for ((name, bytes) in resources) {
                    out.putNextEntry(JarEntry(name))
                    out.write(bytes)
                    out.closeEntry()
                }
            }
        }
    }


    companion object {
        /** Compiles [sources] (qualified name → Java source) in memory; returns binary name → class bytes. */
        fun compile(sources: Map<String, String>): Map<String, ByteArray> {
            val compiler = ToolProvider.getSystemJavaCompiler()
                ?: throw IllegalStateException("No system Java compiler (tests must run on a JDK)")
            val outputs = mutableMapOf<String, ByteArrayOutputStream>()
            val fileManager = object: javax.tools.ForwardingJavaFileManager<javax.tools.StandardJavaFileManager>(
                compiler.getStandardFileManager(null, null, null)
            ) {
                override fun getJavaFileForOutput(
                    location: javax.tools.JavaFileManager.Location,
                    className: String,
                    kind: JavaFileObject.Kind,
                    sibling: javax.tools.FileObject?
                ): JavaFileObject {
                    val buffer = ByteArrayOutputStream()
                    outputs[className] = buffer
                    return object: SimpleJavaFileObject(URI.create("bytes:///" + className.replace('.', '/') + kind.extension), kind) {
                        override fun openOutputStream() = buffer
                    }
                }
            }
            val units = sources.map { (name, source) ->
                object: SimpleJavaFileObject(URI.create("string:///" + name.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
                    override fun getCharContent(ignoreEncodingErrors: Boolean) = source
                }
            }
            val diagnostics = javax.tools.DiagnosticCollector<JavaFileObject>()
            val options = listOf("-parameters", "-classpath", System.getProperty("java.class.path"), "-proc:none")
            val ok = compiler.getTask(null, fileManager, diagnostics, options, null, units).call()
            check(ok) { "Fixture compilation failed:\n" + diagnostics.diagnostics.joinToString("\n") }
            return outputs.mapValues { it.value.toByteArray() }
        }
    }
}
