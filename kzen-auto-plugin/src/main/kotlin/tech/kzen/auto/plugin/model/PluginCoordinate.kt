package tech.kzen.auto.plugin.model


/**
 * Plugin identifier as exposed to external plugins through the published
 * SPI (kzen-auto-plugin).
 *
 * Deliberately minimal: `kzen-auto-plugin` keeps zero dependencies on
 * `kzen-auto-common` and `kzen-lib` to stay a thin SPI surface for
 * out-of-tree plugin authors. The richer Multiplatform counterpart
 * `tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate`
 * lives in `kzen-auto-common` and carries `Digestible`, default-coordinate
 * helpers, and string round-tripping; conversions on the JVM side use
 * the extension functions in
 * `tech.kzen.auto.server.objects.plugin.PluginUtils`.
 */
data class PluginCoordinate(
    val name: String
)
