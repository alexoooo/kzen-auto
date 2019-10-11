package tech.kzen.auto.common.paradigm.imperative.model

import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.imperative.model.control.ControlTransition
import tech.kzen.lib.common.util.Digest


data class ImperativeResponse(
        val executionResult: ExecutionResult?,
        val controlTransition: ControlTransition?,
        val executionModelDigest: Digest
) {
    companion object {
        private const val resultKey = "result"
        private const val transitionKey = "transition"

        @Suppress("UNCHECKED_CAST")
        fun fromCollection(collection: Map<String, Any?>): ImperativeResponse {
            val executionResult = (collection[resultKey] as? Map<String, Any>)?.let {
                ExecutionResult.fromCollection(it)
            }

            val controlTransition = (collection[transitionKey] as? Map<String, Any>)?.let {
                ControlTransition.fromCollection(it)
            }

            return ImperativeResponse(
                    executionResult,
                    controlTransition,
                    Digest.parse(collection[CommonRestApi.fieldDigest] as String)
            )
        }
    }


    fun toCollection(): Map<String, Any?> {
        return mapOf(
                resultKey to executionResult?.toCollection(),
                transitionKey to controlTransition?.toCollection(),
                CommonRestApi.fieldDigest to executionModelDigest.asString()
        )
    }
}