package tech.kzen.auto.server.context

import java.util.Properties


// Version + build timestamp baked into the artifact by the generateBuildInfo Gradle task (a classpath
//  resource, so it travels inside the jar). Loaded at startup and surfaced as logo hover text so the
//  running build is always identifiable. kzen-project reuses this class but reads its own resource
//  (a distinct name avoids a classpath collision with kzen-auto-jvm's own copy on project's classpath).
data class BuildInfo(
    val version: String,
    val timestamp: String?
) {
    fun display(): String {
        return if (timestamp != null) {
            "$version (built $timestamp)"
        }
        else {
            version
        }
    }


    companion object {
        fun load(resource: String): BuildInfo? {
            val stream = BuildInfo::class.java.getResourceAsStream(resource)
                ?: return null

            val properties = Properties()
            stream.use {
                properties.load(it)
            }

            val version = properties.getProperty("version")
                ?: return null

            return BuildInfo(version, properties.getProperty("timestamp"))
        }
    }
}
