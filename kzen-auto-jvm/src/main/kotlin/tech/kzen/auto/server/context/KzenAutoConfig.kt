package tech.kzen.auto.server.context

import tech.kzen.auto.common.api.staticResourcePath


data class KzenAutoConfig(
    val jsModuleName: String,
    val port: Int = 80,
    val host: String = "127.0.0.1"
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val serverPortPrefix = "--server.port="
        private val serverPortRegex = Regex(
            Regex.escape(serverPortPrefix) + "\\d+")

        fun readPort(args: Array<String>): Int? {
            val match = args
                .lastOrNull { it.matches(serverPortRegex) }
                ?: return null

            val portText = match.substring(serverPortPrefix.length)
            return portText.toInt()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun jsFileName(): String {
        return "$jsModuleName.js"
    }

    fun jsResourcePath(): String {
        return "$staticResourcePath/${jsFileName()}"
    }
}