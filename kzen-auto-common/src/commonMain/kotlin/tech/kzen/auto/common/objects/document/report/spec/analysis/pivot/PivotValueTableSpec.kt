@file:Suppress("ConstPropertyName")

package tech.kzen.auto.common.objects.document.report.spec.analysis.pivot

import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class PivotValueTableSpec(
    val columns: Map<String, PivotValueColumnSpec>
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = PivotValueTableSpec(mapOf())

        private const val requestValueTypeDelimiter = "/"


        fun ofRequest(requestParams: RequestParams): PivotValueTableSpec {
            val values = requestParams.values[ReportConventions.previewPivotValuesKey]
                ?: throw IllegalArgumentException(
                    "Request missing '${ReportConventions.previewPivotValuesKey}': $requestParams")

            val columns = values
                .map { encodedColumnValue ->
                    val delimiterIndex = encodedColumnValue.indexOf(requestValueTypeDelimiter)
                    val valueType = PivotValueType.valueOf(encodedColumnValue.substring(0, delimiterIndex))
                    val columnName = encodedColumnValue.substring(delimiterIndex + 1)
                    columnName to valueType
                }
                .groupBy { it.first }
                .mapValues { columnValueGroup ->
                    columnValueGroup.value.map { it.second }
                }
                .mapValues {
                    PivotValueColumnSpec(it.value.toSet())
                }

            return PivotValueTableSpec(columns)
        }


        fun ofNotation(notation: MapAttributeNotation): PivotValueTableSpec {
            val values = mutableMapOf<String, PivotValueColumnSpec>()

            for (e in notation.values) {
                val pivotValueNotation = e.value as ListAttributeNotation
                val pivotValue = PivotValueColumnSpec.ofNotation(pivotValueNotation)
                values[e.key.asString()] = pivotValue
            }

            return PivotValueTableSpec(values)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isEmpty(): Boolean {
        return columns.isEmpty()
    }


    fun asRequest(): RequestParams {
        val encodedValues = columns
            .flatMap { column ->
                column.value.types.map {
                    "${it.name}$requestValueTypeDelimiter${column.key}"
                }
            }

        return RequestParams(mapOf(
            ReportConventions.previewPivotValuesKey to encodedValues))
    }


    override fun digest(sink: Digest.Sink) {
        sink.addUnorderedCollection(columns.entries) {
            addUtf8(it.key)
            addDigestible(it.value)
        }
    }
}