package tech.kzen.auto.common.paradigm.detached.model

import tech.kzen.auto.common.util.RequestParams
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.platform.IoUtils


data class DetachedRequest(
        val parameters: RequestParams,
        val body: ImmutableByteArray?
) {
    companion object {
        private const val parametersKey = "params"
        private const val bodyKey = "body"


        fun fromJsonCollection(collection: Map<String, String?>): DetachedRequest {
            val parameters = RequestParams.parse(collection[parametersKey] as String)
            val body = collection[bodyKey]?.let { ImmutableByteArray.wrap(IoUtils.base64Decode(it)) }
            return DetachedRequest(parameters, body)
        }
    }


    fun getSingle(parameterName: String): String? {
        return parameters.values[parameterName]?.singleOrNull()
    }

    fun getInt(parameterName: String): Int? {
        return getSingle(parameterName)?.toIntOrNull()
    }

    fun getLong(parameterName: String): Long? {
        return getSingle(parameterName)?.toLongOrNull()
    }


    fun toJsonCollection(): Map<String, Any?> {
        return mapOf(
            parametersKey to parameters.asString(),
            bodyKey to body?.let { IoUtils.base64Encode(it.toByteArray()) }
        )
    }
}