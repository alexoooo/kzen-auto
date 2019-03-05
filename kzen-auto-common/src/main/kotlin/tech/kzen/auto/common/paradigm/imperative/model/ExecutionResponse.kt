package tech.kzen.auto.common.paradigm.imperative.model

import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.lib.common.util.Digest


data class ExecutionResponse(
        val executionResult: ExecutionResult,
        val executionModelDigest: Digest
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromCollection(collection: Map<String, Any?>): ExecutionResponse {
            return ExecutionResponse(
                    ExecutionResult.fromCollection(collection["result"] as Map<String, Any>),
                    Digest.parse(collection[CommonRestApi.fieldDigest] as String)
            )
        }
    }


    fun toCollection(): Map<String, Any> {
        return mapOf(
                "result" to executionResult.toCollection(),
                CommonRestApi.fieldDigest to executionModelDigest.asString()
        )
    }
}