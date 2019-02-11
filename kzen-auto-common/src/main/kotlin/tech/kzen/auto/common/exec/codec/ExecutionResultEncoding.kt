package tech.kzen.auto.common.exec.codec

import tech.kzen.auto.common.exec.ExecutionError
import tech.kzen.auto.common.exec.ExecutionResult
import tech.kzen.auto.common.exec.ExecutionSuccess
import tech.kzen.auto.common.objects.service.ActionManager
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.IoUtils


data class ExecutionResultEncoding(
        val errorMessage: String?,
        val value: ByteArray?,
        val detail: ByteArray?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun encode(
                executionResult: ExecutionResult,
                objectLocation: ObjectLocation,
                actionManager: ActionManager
        ): ExecutionResultEncoding {
            return when (executionResult) {
                is ExecutionError ->
                    ExecutionResultEncoding(
                            executionResult.errorMessage,
                            null,
                            null
                    )

                is ExecutionSuccess -> {
                    val actionHandle = actionManager.get(objectLocation)

                    ExecutionResultEncoding(
                            null,
                            executionResult.value?.let(actionHandle.valueCodec::encode),
                            executionResult.detail?.let(actionHandle.detailCodec::encode)
                    )
                }
            }
        }

        fun toCollection(result: ExecutionResultEncoding): Map<String, String?> {
            return mapOf(
                    "error" to result.errorMessage,
                    "value" to result.value?.let { IoUtils.base64Encode(it) },
                    "detail" to result.detail?.let { IoUtils.base64Encode(it) }
            )
        }


        fun fromCollection(collection: Map<String, String?>): ExecutionResultEncoding {
            return ExecutionResultEncoding(
                            collection["error"],
                            collection["value"]?.let { IoUtils.base64Decode(it) },
                            collection["detail"]?.let { IoUtils.base64Decode(it) }
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun decode(): ExecutionResult {
        TODO()
    }


    fun digest(): Digest {
        val digest = Digest.Streaming()

        digest.addUtf8(errorMessage)
        digest.addBytes(value)
        digest.addBytes(detail)

        return digest.digest()
    }


    //-----------------------------------------------------------------------------------------------------------------
    // NB: these are required for ByteArray

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as ExecutionResultEncoding

        if (errorMessage != other.errorMessage) {
            return false
        }

        val valueEquals =
                if (value == null || other.value == null) {
                    value == null && other.value == null
                }
                else {
                    value.contentEquals(other.value)
                }
        if (! valueEquals) {
            return false
        }

        val detailEquals =
                if (detail == null || other.detail == null) {
                    detail == null && other.detail == null
                }
                else {
                    detail.contentEquals(other.detail)
                }
        if (! detailEquals) {
            return false
        }

        return true
    }


    override fun hashCode(): Int {
        var result = errorMessage?.hashCode() ?: 0
        result = 31 * result + (value?.contentHashCode() ?: 0)
        result = 31 * result + (detail?.contentHashCode() ?: 0)
        return result
    }
}