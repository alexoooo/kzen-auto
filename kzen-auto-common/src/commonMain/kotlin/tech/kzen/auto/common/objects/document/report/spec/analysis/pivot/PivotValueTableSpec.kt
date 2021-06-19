package tech.kzen.auto.common.objects.document.report.spec.analysis.pivot

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class PivotValueTableSpec(
    val columns: Map<String, PivotValueColumnSpec>
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = PivotValueTableSpec(mapOf())

//        private const val requestValueTypeDelimiter = "/"

//        fun ofPreviewRequest(request: DetachedRequest): PivotValueTableSpec {
//            val values = request.parameters.values[ReportConventions.previewPivotValuesKey]
//                ?: throw IllegalArgumentException(
//                    "Request missing '${ReportConventions.previewPivotValuesKey}': $request")
//
//            val columns = values
//                .map { encodedColumnValue ->
//                    val delimiterIndex = encodedColumnValue.indexOf(requestValueTypeDelimiter)
//                    val valueType = PivotValueType.valueOf(encodedColumnValue.substring(0, delimiterIndex))
//                    val columnName = encodedColumnValue.substring(delimiterIndex + 1)
//                    columnName to valueType
//                }.groupBy { columnValue ->
//                    columnValue.first
//                }.mapValues { columnValueGroup ->
//                    columnValueGroup.value.map { it.second }
//                }.mapValues {
//                    PivotValueColumnSpec(it.value.toSet())
//                }
//
//            return PivotValueTableSpec(columns)
//        }


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
    fun headerListing(): HeaderListing {
        return HeaderListing(columns.keys.toList())
    }


    fun isEmpty(): Boolean {
        return columns.isEmpty()
    }


//    fun toPreviewRequest(): DetachedRequest {
//        val encodedValues = columns
//            .flatMap { column ->
//                column.value.types.map {
//                    "${it.name}$requestValueTypeDelimiter${column.key}"
//                }
//            }
//
//        return DetachedRequest(
//            RequestParams(mapOf(
//                ReportConventions.previewPivotValuesKey to encodedValues
//            )),
//            null)
//    }


    override fun digest(builder: Digest.Builder) {
        builder.addDigestibleUnorderedMap(columns.mapKeys { Digest.ofUtf8(it.key) })
    }
}