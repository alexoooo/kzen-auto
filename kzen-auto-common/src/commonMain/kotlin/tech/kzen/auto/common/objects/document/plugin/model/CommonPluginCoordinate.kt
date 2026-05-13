package tech.kzen.auto.common.objects.document.plugin.model

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


/**
 * Multiplatform plugin identifier used across `kzen-auto-common`,
 * `kzen-auto-js`, and `kzen-auto-jvm`. Implements [Digestible] and carries
 * default-coordinate / `ofString` helpers.
 *
 * The dependency-minimal SPI counterpart visible to out-of-tree plugins
 * is `tech.kzen.auto.plugin.model.PluginCoordinate` in `kzen-auto-plugin`
 * — kept thin because that module has no `kzen-lib` dependency. JVM-side
 * conversion in either direction goes through
 * `tech.kzen.auto.server.objects.plugin.PluginUtils.asCommon` /
 * `asPluginCoordinate`.
 */
data class CommonPluginCoordinate(
    val name: String
):
    Digestible
{
    companion object {
        const val defaultName = ""
        val defaultCoordinate = CommonPluginCoordinate(defaultName)


        fun ofString(asString: String): CommonPluginCoordinate {
            return CommonPluginCoordinate(asString)
        }
    }


    fun isDefault(): Boolean {
        return name == defaultName
    }


    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(name)
    }


    fun asString(): String {
        return name
    }


    override fun toString(): String {
        return asString()
    }
}