package tech.kzen.auto.common.objects.document.plugin.model


data class CommonPluginCoordinate(
    val name: String
) {
    companion object {
        fun ofString(asString: String): CommonPluginCoordinate {
            return CommonPluginCoordinate(asString)
        }
    }


    fun asString(): String {
        return name
    }
}