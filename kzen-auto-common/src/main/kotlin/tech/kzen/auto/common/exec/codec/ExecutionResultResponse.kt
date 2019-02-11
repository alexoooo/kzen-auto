package tech.kzen.auto.common.exec.codec

import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.exec.ExecutionError
import tech.kzen.auto.common.exec.ExecutionResult
import tech.kzen.auto.common.exec.ExecutionSuccess
import tech.kzen.auto.common.objects.service.ActionManager
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.IoUtils


data class ExecutionResultResponse(
        val resultEncoding: ExecutionResultEncoding,
        val executionModelDigest: Digest
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun toCollection(response: ExecutionResultResponse): Map<String, String?> {
            return mapOf(
                    "error" to response.resultEncoding.errorMessage,
                    "value" to response.resultEncoding.value?.let { IoUtils.base64Encode(it) },
                    "detail" to response.resultEncoding.detail?.let { IoUtils.base64Encode(it) },
                    CommonRestApi.fieldDigest to response.executionModelDigest.asString()
            )
        }


        fun fromCollection(collection: Map<String, String?>): ExecutionResultResponse {
            return ExecutionResultResponse(
                    ExecutionResultEncoding(
                            collection["error"],
                            collection["value"]?.let { IoUtils.base64Decode(it) },
                            collection["detail"]?.let { IoUtils.base64Decode(it) }
                    ),
                    Digest.parse(collection[CommonRestApi.fieldDigest]!!)
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toResult(actionHandle: ActionManager.Handle): ExecutionResult {
        if (resultEncoding.errorMessage != null) {
            return ExecutionError(resultEncoding.errorMessage)
        }

        val decodedValue = resultEncoding.value?.let{ actionHandle.valueCodec.decode(it) }
        val decodedDetail = resultEncoding.detail?.let{ actionHandle.detailCodec.decode(it) }

        return ExecutionSuccess(decodedValue, decodedDetail)
    }


//    //-----------------------------------------------------------------------------------------------------------------
//    fun digest(): Digest {
//        val digest = Digest.Streaming()
//
//        digest.addUtf8(resultEncoding.errorMessage)
//        digest.addBytes(resultEncoding.value)
//        digest.addBytes(resultEncoding.detail)
//        digest.addDigest(executionModelDigest)
//
//        return digest.digest()
//    }
}