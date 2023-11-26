package tech.kzen.auto.common.objects.document.plugin.model

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


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