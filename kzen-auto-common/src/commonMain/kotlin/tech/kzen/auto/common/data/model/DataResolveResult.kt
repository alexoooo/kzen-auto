package tech.kzen.auto.common.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue


/**
 * A point-in-time manifest plus open diagnostic kinds; see
 * `kzen/docs/analysis/2026-08-20_job-data-source.md` §3.2.
 */
@Serializable
data class DataResolveResult(
    @SerialName(DataModelKeys.manifest)
    val manifest: DataManifest,
    @SerialName(DataModelKeys.diagnostics)
    val diagnostics: List<DataDiagnostic>
) {
    companion object {
        fun ofExecutionValue(value: ExecutionValue): DataResolveResult {
            val map = value.requiredModelMap("DataResolveResult")
            return DataResolveResult(
                DataManifest.ofExecutionValue(map.requiredMap(DataModelKeys.manifest)),
                map.requiredList(DataModelKeys.diagnostics).values.map(DataDiagnostic::ofExecutionValue)
            )
        }
    }


    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(mapOf(
            DataModelKeys.manifest to manifest.asExecutionValue(),
            DataModelKeys.diagnostics to ListExecutionValue(diagnostics.map { it.asExecutionValue() })
        ))
    }
}
