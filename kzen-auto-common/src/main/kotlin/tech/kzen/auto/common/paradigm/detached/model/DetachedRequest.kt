package tech.kzen.auto.common.paradigm.detached.model

import tech.kzen.lib.common.util.ImmutableByteArray


data class DetachedRequest(
        val parameters: Map<String, List<String>>,
        val body: ImmutableByteArray?
) {
    fun getSingle(parameterName: String): String? {
        return parameters[parameterName]?.singleOrNull()
    }

    fun getInt(parameterName: String): Int? {
        return getSingle(parameterName)?.toIntOrNull()
    }
}