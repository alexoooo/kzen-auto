package tech.kzen.auto.common.util


data class RequestParams(
        val values: Map<String, List<String>>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = RequestParams(mapOf())


        fun parse(paramsLine: String): RequestParams {
            val params = paramsLine.split('&')

            val buffer = mutableMapOf<String, MutableList<String>>()

            for (param in params) {
                val equalsIndex = param.indexOf('=')
                val key = param.substring(0, equalsIndex)
                val value = param.substring(equalsIndex + 1)

                val values = buffer.getOrPut(key) { mutableListOf() }

                values.add(value)
            }

            return RequestParams(buffer)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun set(key: String, value: String): RequestParams {
        return RequestParams(
                values.plus(key to listOf(value)))
    }


    fun get(key: String): String? {
        return values[key].orEmpty().singleOrNull()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        val entries = mutableListOf<String>()
        for (e in values) {
            for (value in e.value) {
                entries.add(e.key + "=" + value)
            }
        }
        return entries.joinToString("&")
    }
}