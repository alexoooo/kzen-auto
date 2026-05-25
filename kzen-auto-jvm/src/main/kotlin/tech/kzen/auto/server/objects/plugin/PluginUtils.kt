package tech.kzen.auto.server.objects.plugin

import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.server.objects.plugin.PluginUtils.digestPluginCoordinate
import tech.kzen.lib.common.util.digest.Digest


/**
 * JVM-side bridge between the SPI-facing
 * [tech.kzen.auto.plugin.model.PluginCoordinate] and the multiplatform
 * [tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate].
 * The two types exist separately so that `kzen-auto-plugin` can stay
 * dependency-minimal (no `kzen-lib`, no `kzen-auto-common`); this object
 * is where the JVM stack converts at module boundaries.
 *
 * [digestPluginCoordinate] is the allocation-free alternative to
 * `coordinate.asCommon().digest(sink)` — used in hot digest paths
 * (e.g. [tech.kzen.auto.server.objects.report.exec.input.model.data.FlatDataInfo]).
 */
object PluginUtils {
    fun digestPluginCoordinate(pluginCoordinate: PluginCoordinate, sink: Digest.Sink) {
        sink.addUtf8(pluginCoordinate.name)
    }


    fun CommonPluginCoordinate.asPluginCoordinate(): PluginCoordinate {
        check(name != CommonPluginCoordinate.defaultName)
        return PluginCoordinate(name)
    }


    fun PluginCoordinate.asCommon(): CommonPluginCoordinate {
        return CommonPluginCoordinate(name)
    }
}