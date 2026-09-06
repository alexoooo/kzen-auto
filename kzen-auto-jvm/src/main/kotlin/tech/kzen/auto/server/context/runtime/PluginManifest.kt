package tech.kzen.auto.server.context.runtime

import tech.kzen.lib.common.util.yaml.YamlMap
import tech.kzen.lib.common.util.yaml.YamlParser
import tech.kzen.lib.common.util.yaml.YamlString


/**
 * The optional `META-INF/kzen/plugin.yaml` of a folder plugin: metadata only, never a class allow-list. Every
 * key is optional; an unknown key is an error so a misspelt key cannot silently mean "no constraint".
 *
 * ```yaml
 * id: my-plugin        # overrides the directory name
 * version: 1.2.0
 * spi: 1               # PluginSpiVersion.current this plugin was built against
 * ```
 */
data class PluginManifest(
    val id: String?,
    val version: String?,
    val spiVersion: Int?
) {
    companion object {
        const val resourcePath = "META-INF/kzen/plugin.yaml"

        private const val idKey = "id"
        private const val versionKey = "version"
        private const val spiKey = "spi"
        private val knownKeys = setOf(idKey, versionKey, spiKey)

        val empty = PluginManifest(null, null, null)


        fun parse(yaml: String): PluginManifest {
            if (yaml.isBlank()) {
                return empty
            }
            val node = YamlParser.parse(yaml)
            val map = node as? YamlMap
                ?: throw IllegalArgumentException("$resourcePath must be a map, got ${node::class.simpleName}")

            val unknown = map.values.keys - knownKeys
            require(unknown.isEmpty()) { "$resourcePath has unknown keys $unknown (known: $knownKeys)" }

            val id = map.text(idKey)
            require(id == null || id.isNotBlank()) { "$resourcePath id must not be blank" }
            val spi = map.text(spiKey)?.let {
                it.toIntOrNull() ?: throw IllegalArgumentException("$resourcePath spi must be an integer, got '$it'")
            }
            return PluginManifest(id, map.text(versionKey), spi)
        }


        private fun YamlMap.text(key: String): String? {
            val value = values[key] ?: return null
            return (value as? YamlString)?.value
                ?: throw IllegalArgumentException("$resourcePath $key must be a scalar")
        }
    }
}
