package tech.kzen.auto.common.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


/** Flat, ordered resolution result; units never nest. See `kzen/docs/analysis/2026-08-20_job-data-source.md` §3.4. */
@Serializable
data class DataManifest(
    @SerialName(DataModelKeys.units)
    val units: List<DataUnit>
): Digestible {
    companion object {
        fun ofExecutionValue(value: ExecutionValue): DataManifest {
            val map = value.requiredModelMap("DataManifest")
            return DataManifest(
                map.requiredList(DataModelKeys.units).values.map(DataUnit::ofExecutionValue)
            )
        }
    }


    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(mapOf(
            DataModelKeys.units to ListExecutionValue(units.map { it.asExecutionValue() })
        ))
    }


    override fun digest(sink: Digest.Sink) {
        sink.addDigestibleList(units)
    }
}
