package tech.kzen.auto.common.objects.document.report.spec.filter

import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.data.schema.HeaderLabel
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.codec.NotationCodec
import tech.kzen.lib.common.model.structure.notation.codec.NotationCodecs
import tech.kzen.lib.common.model.structure.notation.codec.xmap
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.objects.general.CodecAttributeDefiner
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class FilterSpec(
//    val columns: HeaderLabelMap<ColumnFilterSpec>
    val columns: Map<HeaderLabel, ColumnFilterSpec>
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // A `filter` notation is a column-key -> {type, values} map; insertion order is preserved. This codec
        // is the single source of truth for that layout — the read side ([ofNotation], also used by the JS
        // ValueSetFilterEditor and the server-side [Definer]) and the write side both derive from it.
        val codec: NotationCodec<FilterSpec> =
            NotationCodecs.map({ HeaderLabel.ofString(it) }, { it.asString() }, ColumnFilterSpec.codec)
                .xmap({ FilterSpec(it) }, { it.columns })


        fun ofNotation(attributeNotation: MapAttributeNotation): FilterSpec {
            return codec.parse(attributeNotation)
        }


        fun addCommand(mainLocation: ObjectLocation, columnName: String): NotationCommand {
            val columnAttributeSegment = AttributeSegment.ofKey(columnName)
            return InsertMapEntryInAttributeCommand(
                mainLocation,
                ReportConventions.filterAttributePath,
                PositionRelation.afterLast,
                columnAttributeSegment,
                ColumnFilterSpec.emptyNotation,
                true)
        }


        fun removeCommand(mainLocation: ObjectLocation, columnName: HeaderLabel): NotationCommand {
            return RemoveInAttributeCommand(
                mainLocation,
                columnAttributePath(columnName),
                true)
        }


        fun addValueCommand(
            mainLocation: ObjectLocation,
            columnName: HeaderLabel,
            filterValue: String
        ): NotationCommand {
            return InsertListItemInAttributeCommand(
                mainLocation,
                columnValuesAttributePath(columnName),
                PositionRelation.afterLast,
                ScalarAttributeNotation(filterValue))
        }


        fun removeValueCommand(
            mainLocation: ObjectLocation,
            columnName: HeaderLabel,
            filterValue: String
        ): NotationCommand {
            return RemoveListItemInAttributeCommand(
                mainLocation,
                columnValuesAttributePath(columnName),
                ScalarAttributeNotation(filterValue),
                false)
        }


        fun updateTypeCommand(
            mainLocation: ObjectLocation,
            columnName: HeaderLabel,
            filterType: ColumnFilterType
        ): NotationCommand {
            return UpdateInAttributeCommand(
                mainLocation,
                columnTypeAttributePath(columnName),
                ScalarAttributeNotation(filterType.name))
        }


        private fun columnAttributePath(columnName: HeaderLabel): AttributePath {
            val columnAttributeSegment = AttributeSegment.ofKey(columnName.asString())
            return ReportConventions.filterAttributePath.nest(columnAttributeSegment)
        }


        fun columnValuesAttributePath(columnName: HeaderLabel): AttributePath {
            val columnAttributePath = columnAttributePath(columnName)
            return columnAttributePath.nest(ColumnFilterSpec.valuesAttributeSegment)
        }


        private fun columnTypeAttributePath(columnName: HeaderLabel): AttributePath {
            val columnAttributePath = columnAttributePath(columnName)
            return columnAttributePath.nest(ColumnFilterSpec.typeAttributeSegment)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Reads the `filter` attribute verbatim (inheritanceMerge = false, matching the prior firstAttribute read —
    // insertion order is significant for the column list).
    @Reflect
    object Definer: CodecAttributeDefiner<FilterSpec>(codec, inheritanceMerge = false)


    //-----------------------------------------------------------------------------------------------------------------
    fun toRunSignature(): FilterSpec {
        return FilterSpec(
            columns.filterValues { !it.isEmpty() })
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addUnorderedCollection(columns.entries) {
            addDigestible(it.key)
            addDigestible(it.value)
        }
    }
}