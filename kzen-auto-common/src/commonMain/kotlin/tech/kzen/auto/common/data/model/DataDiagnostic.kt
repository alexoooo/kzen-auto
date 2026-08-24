package tech.kzen.auto.common.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue


/**
 * Open, text-canonical diagnostic returned while resolving data; see
 * `kzen/docs/analysis/2026-08-20_job-data-source.md` §3.2.
 */
@Serializable
data class DataDiagnostic(
    @SerialName(DataModelKeys.kind)
    val kind: String,
    @SerialName(DataModelKeys.message)
    val message: String
) {
    @Suppress("ConstPropertyName")
    companion object {
        const val skipped = "skipped"
        const val unsupported = "unsupported"


        fun ofExecutionValue(value: ExecutionValue): DataDiagnostic {
            val map = value.requiredModelMap("DataDiagnostic")
            return DataDiagnostic(
                map.requiredText(DataModelKeys.kind),
                map.requiredText(DataModelKeys.message)
            )
        }
    }


    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(mapOf(
            DataModelKeys.kind to TextExecutionValue(kind),
            DataModelKeys.message to TextExecutionValue(message)
        ))
    }
}
