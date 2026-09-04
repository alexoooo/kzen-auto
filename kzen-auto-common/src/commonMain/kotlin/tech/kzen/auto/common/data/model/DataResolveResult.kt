package tech.kzen.auto.common.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue


/**
 * A point-in-time manifest plus open diagnostic kinds; see
 * `kzen/docs/analysis/2026-08-20_job-data-source.md` §3.2.
 */
@Serializable
data class DataResolveResult(
    @SerialName(DataModelKeys.manifest)
    val manifest: DataManifest,
    @SerialName(DataModelKeys.diagnostics)
    val diagnostics: List<DataDiagnostic>,
    @SerialName(DataModelKeys.resolutionDetails)
    val resolutionDetails: List<FormatResolutionDetail> = emptyList()
) {
    companion object {
        fun ofExecutionValue(value: ExecutionValue): DataResolveResult {
            val map = value.requiredModelMap("DataResolveResult")
            return DataResolveResult(
                DataManifest.ofExecutionValue(map.requiredMap(DataModelKeys.manifest)),
                map.requiredList(DataModelKeys.diagnostics).values.map(DataDiagnostic::ofExecutionValue),
                map.optionalList(DataModelKeys.resolutionDetails)
                    ?.values
                    ?.map(::detailOfExecutionValue)
                    .orEmpty()
            )
        }


        private fun detailOfExecutionValue(value: ExecutionValue): FormatResolutionDetail {
            val map = value.requiredModelMap("FormatResolutionDetail")
            return FormatResolutionDetail(
                DataRef.ofExecutionValue(map.requiredMap(DataModelKeys.ref)),
                map.requiredNullableText("concreteFormatReference"),
                map.requiredText("displayLabel"),
                FormatSelectionKind.ofWireValue(map.requiredText("selection")),
                FormatResolutionBasis.ofWireValue(map.requiredText("basis")),
                map.requiredText("reason"),
                map.requiredNullableText("warning"),
                map.values[DataModelKeys.role]?.let {
                    DataRole((it as? TextExecutionValue)?.value
                        ?: throw IllegalArgumentException("'${DataModelKeys.role}' must be text: $map"))
                } ?: DataRole.main,
                map.values["resolvedEncoding"]?.let {
                    when (it) {
                        NullExecutionValue -> null
                        is TextExecutionValue -> it.value
                        else -> throw IllegalArgumentException("'resolvedEncoding' must be text or null: $map")
                    }
                },
                map.values["columnsLocked"]?.let {
                    (it as? tech.kzen.lib.common.exec.BooleanExecutionValue)?.value
                        ?: throw IllegalArgumentException("'columnsLocked' must be boolean: $map")
                } ?: false)
        }
    }


    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(mapOf(
            DataModelKeys.manifest to manifest.asExecutionValue(),
            DataModelKeys.diagnostics to ListExecutionValue(diagnostics.map { it.asExecutionValue() }),
            DataModelKeys.resolutionDetails to ListExecutionValue(resolutionDetails.map(::detailExecutionValue))
        ))
    }


    private fun detailExecutionValue(detail: FormatResolutionDetail): ExecutionValue {
        return MapExecutionValue(linkedMapOf(
            DataModelKeys.ref to detail.ref.asExecutionValue(),
            "concreteFormatReference" to nullableText(detail.concreteFormatReference),
            "displayLabel" to TextExecutionValue(detail.displayLabel),
            "selection" to TextExecutionValue(detail.selection.wireValue),
            "basis" to TextExecutionValue(detail.basis.wireValue),
            "reason" to TextExecutionValue(detail.reason),
            "warning" to nullableText(detail.warning),
            DataModelKeys.role to TextExecutionValue(detail.role.name),
            "resolvedEncoding" to nullableText(detail.resolvedEncoding),
            "columnsLocked" to tech.kzen.lib.common.exec.BooleanExecutionValue(detail.columnsLocked)))
    }


    private fun nullableText(value: String?): ExecutionValue =
        value?.let(::TextExecutionValue) ?: NullExecutionValue
}
