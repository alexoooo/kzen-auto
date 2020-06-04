package tech.kzen.auto.client.objects.document.filter

import kotlinx.css.*
import react.*
import react.dom.*
import styled.*
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.ExpandLessIcon
import tech.kzen.auto.client.wrap.ExpandMoreIcon
import tech.kzen.auto.client.wrap.MaterialCheckbox
import tech.kzen.auto.client.wrap.MaterialIconButton
import tech.kzen.auto.common.objects.document.filter.FilterConventions
import tech.kzen.auto.common.paradigm.reactive.ColumnSummary
import tech.kzen.auto.common.paradigm.reactive.NominalValueSummary
import tech.kzen.auto.common.paradigm.reactive.OpaqueValueSummary
import tech.kzen.auto.common.paradigm.reactive.StatisticValueSummary
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionIndex
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertListItemInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertMapEntryInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveInAttributeCommand
import tech.kzen.lib.platform.collect.persistentListOf


class FilterColumn(
    props: Props
):
    RPureComponent<FilterColumn.Props, FilterColumn.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val maxValueLength = 96
        private const val abbreviationSuffix = "..."
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var mainLocation: ObjectLocation,
        var clientState: SessionState,
        var requiredValues: Set<String>?,

        var columnIndex: Int,
        var columnName: String,
        var columnSummary: ColumnSummary?
    ): RProps


    class State(
        var open: Boolean,
//        var requested: Boolean,
        var error: String?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        open = false
//        requested = false
//        valueSummary = null
        error = null
    }


    override fun componentDidUpdate(
        prevProps: Props,
        prevState: State,
        snapshot: Any
    ) {
//        if (state.open && ! prevState.open && ! state.requested) {
//            setState {
//                requested = true
//            }
//        }

//        if (state.requested && ! prevState.requested) {
//            requestValueSummary()
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onCriteriaChange(
        value: String,
//        previousChecked: Boolean,
        columnRequired: Set<String>
    ) {
        val valueIndex = columnRequired.indexOf(value)
        val columnAttributeSegment = AttributeSegment.ofKey(props.columnName)
        val columnRequiredPath = AttributePath(
            FilterConventions.criteriaAttributeName,
            AttributeNesting(persistentListOf(columnAttributeSegment)))

        async {
            if (valueIndex == -1) {
                if (columnRequired.isEmpty()) {
                    ClientContext.mirroredGraphStore.apply(
                        InsertMapEntryInAttributeCommand(
                            props.mainLocation,
                            columnRequiredPath.parent(),
                            PositionIndex(0),
                            columnAttributeSegment,
                            ListAttributeNotation(
                                persistentListOf(ScalarAttributeNotation(value))
                            ),
                            true
                        ))
                }
                else {
                    ClientContext.mirroredGraphStore.apply(
                        InsertListItemInAttributeCommand(
                            props.mainLocation,
                            columnRequiredPath,
                            PositionIndex(columnRequired.size),
                            ScalarAttributeNotation(value)))
                }
            }
            else {
                val itemPath = columnRequiredPath.nesting.push(AttributeSegment.ofIndex(valueIndex))
                ClientContext.mirroredGraphStore.apply(
                    RemoveInAttributeCommand(
                        props.mainLocation,
                        columnRequiredPath.copy(nesting = itemPath),
                        true))
            }
        }
    }


    private fun onOpenToggle() {
        setState {
            open = ! open
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun formatCount(count: Long): String {
        return count.toString()
            .replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")
    }


    private fun abbreviateValue(value: String): String {
        if (value.length < maxValueLength) {
            return value
        }

        val truncated = value.substring(0, maxValueLength - abbreviationSuffix.length)
        return truncated + abbreviationSuffix
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val columnSummary = props.columnSummary

        styledDiv {
            css {
                borderTopWidth = 1.px
                borderTopStyle = BorderStyle.solid
                borderTopColor = Color.gray
            }

            renderCardHeader(columnSummary)

            val error = state.error
            if (error != null) {
                div {
                    +"Error: $error"
                }
            }

            val requiredValues = props.requiredValues
            if (requiredValues != null) {
                div {
                    +"Required values: ${requiredValues.joinToString()}"
                }
            }

            if (state.open) {
                renderCardBody(columnSummary)
            }
        }
    }


    private fun RBuilder.renderCardHeader(valueSummary: ColumnSummary?) {
        styledTable {
            css {
                width = 100.pct
            }

            tbody {
                tr {
                    styledTd {
                        css {
                            width = 100.pct.minus(20.em)
                        }

                        styledSpan {
                            css {
                                fontSize = 2.em
                            }

                            +"#${props.columnIndex + 1} ${props.columnName}"
                        }
                    }

                    styledTd {
                        css {
                            width = 20.em
                            textAlign = TextAlign.right
                        }

                        if (valueSummary != null) {
                            val countFormat = formatCount(valueSummary.count)
                            +"Count: $countFormat"
                        }

                        child(MaterialIconButton::class) {
                            attrs {
                                onClick = {
                                    onOpenToggle()
                                }
                            }

                            if (state.open) {
                                child(ExpandLessIcon::class) {}
                            }
                            else {
                                child(ExpandMoreIcon::class) {}
                            }
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderCardBody(valueSummary: ColumnSummary?) {
        styledDiv {
            css {
                width = 100.pct
            }

            if (valueSummary == null) {
                +"Loading..."
                return
            }

            val hasNominal = ! valueSummary.nominalValueSummary.isEmpty()
            if (hasNominal) {
                renderHistogram(valueSummary.nominalValueSummary)
            }

            val hasNumeric = ! valueSummary.numericValueSummary.isEmpty()
            if (hasNumeric && hasNominal) {
                br {}
            }

            if (hasNumeric) {
                renderNumeric(valueSummary.numericValueSummary)
            }

            val hasOpaque = ! valueSummary.opaqueValueSummary.isEmpty()
            if (hasOpaque && (hasNumeric || hasNominal)) {
                br {}
            }

            if (hasOpaque) {
                renderOpaque(valueSummary.opaqueValueSummary)
            }
        }
    }


    private fun RBuilder.renderHistogram(histogram: NominalValueSummary) {
        styledDiv {
            css {
                maxHeight = 20.em
                overflowY = Overflow.auto
            }

            table {
                styledThead {
                    tr {
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color.white
                            }
                            +"Filter"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color.white
                            }
                            +"Value"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color.white
                            }
                            +"Count"
                        }
                    }
                }

                tbody {
                    for (e in histogram.histogram.entries) {
                        val columnRequired = props.requiredValues
                            ?: setOf()
                        val checked = columnRequired.contains(e.key)

                        tr {
                            key = e.key

                            td {
                                child(MaterialCheckbox::class) {
                                    attrs {
                                        this.checked = checked

                                        onChange = {
                                            onCriteriaChange(e.key, columnRequired)
                                        }
                                    }
                                }
                            }

                            td {
                                val abbreviated = abbreviateValue(e.key)
                                +abbreviated
                            }

                            td {
                                +formatCount(e.value)
                            }
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderNumeric(density: StatisticValueSummary) {
        styledDiv {
            css {
                maxHeight = 20.em
                overflowY = Overflow.auto
            }

            table {
                styledThead {
                    tr {
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color.white
                            }
                            +"Statistic"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color.white
                            }
                            +"Value"
                        }
//                        styledTh {
//                            css {
//                                position = Position.sticky
//                                top = 0.px
//                                backgroundColor = Color.white
//                            }
//                            +"Count"
//                        }
                    }
                }

                tbody {
                    tr {
                        td { +"Count" }
                        td {
                            +"${density.count}"
                        }
                    }

                    tr {
                        td { +"Sum" }
                        td {
                            +"${density.sum}"
                        }
                    }

                    tr {
                        td { +"Minimum" }
                        td {
                            +"${density.min}"
                        }
                    }

                    tr {
                        td { +"Maximum" }
                        td {
                            +"${density.max}"
                        }
                    }

                    tr {
                        td { +"Average" }
                        td {
                            +"${density.sum / density.count}"
                        }
                    }

//                    for (e in density.density.entries) {
//                        tr {
//                            key = e.key.toString()
//
//                            td {
//                                +e.key.start.toString()
//                            }
//
//                            td {
//                                +e.key.endInclusive.toString()
//                            }
//
//                            td {
//                                +formatCount(e.value)
//                            }
//                        }
//                    }
                }
            }
        }
    }


    private fun RBuilder.renderOpaque(opaque: OpaqueValueSummary) {
        styledDiv {
            css {
                maxHeight = 20.em

                overflowY = Overflow.auto
            }

            table {
                styledThead {
                    tr {
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color.white
                            }
                            +"Random Sample"
                        }
                    }
                }

                tbody {
                    for (value in opaque.sample) {
                        tr {
                            key = value

                            td {
                                val abbreviated = abbreviateValue(value)
                                +abbreviated
                            }
                        }
                    }
                }
            }
        }
    }
}