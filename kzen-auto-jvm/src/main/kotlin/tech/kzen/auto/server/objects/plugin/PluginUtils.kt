package tech.kzen.auto.server.objects.plugin

import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.lib.common.util.digest.Digest


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