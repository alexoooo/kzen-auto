package tech.kzen.auto.common.paradigm.detached.model

import tech.kzen.auto.common.util.RequestParams
import tech.kzen.lib.common.util.ImmutableByteArray


data class DetachedRequest(
        val parameters: RequestParams,
        val body: ImmutableByteArray?
) {
    fun getSingle(parameterName: String): String? {
        return parameters.values[parameterName]?.singleOrNull()
    }

    fun getInt(parameterName: String): Int? {
        return getSingle(parameterName)?.toIntOrNull()
    }
}