package tech.kzen.auto.client.objects.document.filter

import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import react.dom.*
import styled.*
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.MaterialCardContent
import tech.kzen.auto.client.wrap.MaterialCheckbox
import tech.kzen.auto.client.wrap.MaterialPaper
import tech.kzen.auto.common.objects.document.filter.FilterConventions
import tech.kzen.auto.common.paradigm.reactive.NominalValueSummary
import tech.kzen.auto.common.paradigm.reactive.NumericValueSummary
import tech.kzen.auto.common.paradigm.reactive.OpaqueValueSummary
import tech.kzen.auto.common.paradigm.reactive.ValueSummary
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


class ColumnFilter(
    props: Props
):
    RPureComponent<ColumnFilter.Props, ColumnFilter.State>(props)
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
//        var criteriaSpec: CriteriaSpec,
        var requiredValues: Set<String>?,

        var columnIndex: Int,
        var columnHeader: String,
        var valueSummary: ValueSummary?
    ): RProps


    class State(

    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    private fun onCriteriaChange(
        value: String,
//        previousChecked: Boolean,
        columnRequired: Set<String>
    ) {
        val valueIndex = columnRequired.indexOf(value)
        val columnAttributeSegment = AttributeSegment.ofKey(props.columnHeader)
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
                            )
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
                if (columnRequired.size == 1) {
                    ClientContext.mirroredGraphStore.apply(
                        RemoveInAttributeCommand(
                            props.mainLocation,
                            columnRequiredPath))
                }
                else {
                    val itemPath = columnRequiredPath.nesting.push(AttributeSegment.ofIndex(valueIndex))
                    ClientContext.mirroredGraphStore.apply(
                        RemoveInAttributeCommand(
                            props.mainLocation,
                            columnRequiredPath.copy(nesting = itemPath)))
                }
            }
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
        val valueSummary = props.valueSummary

        child(MaterialPaper::class) {
//            attrs {
//                style = reactStyles
//            }

            child(MaterialCardContent::class) {
                renderCardHeader(valueSummary)

                if (valueSummary != null) {
                    renderCardContent(valueSummary)
                }
            }
        }
    }


    private fun RBuilder.renderCardHeader(valueSummary: ValueSummary?) {
        styledDiv {
            css {
                width = 100.pct
            }

            styledSpan {
                css {
                    fontSize = 2.em
                }

                +"#${props.columnIndex + 1} ${props.columnHeader}"
            }

            styledDiv {
                css {
                    float = Float.right
                    fontWeight = FontWeight.bold
                }

                if (valueSummary == null) {
                    +"..."
                } else {
                    val countFormat = formatCount(valueSummary.count)
                    +"Count: $countFormat"
                }
            }
        }
    }


    private fun RBuilder.renderCardContent(valueSummary: ValueSummary) {
        val hasNominal = ! valueSummary.nominalValueSummary.isEmpty()
        if (hasNominal) {
            renderHistogram(valueSummary.nominalValueSummary)
        }

        val hasNumeric = ! valueSummary.numericValueSummary.isEmpty()
        if (hasNumeric && hasNominal) {
            br {}
        }

        if (hasNumeric) {
            renderDensity(valueSummary.numericValueSummary)
        }

        val hasOpaque = ! valueSummary.opaqueValueSummary.isEmpty()
        if (hasOpaque && (hasNumeric || hasNominal)) {
            br {}
        }

        if (hasOpaque) {
            renderOpaque(valueSummary.opaqueValueSummary)
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


    private fun RBuilder.renderDensity(density: NumericValueSummary) {
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
                            +"From"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color.white
                            }
                            +"To"
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
                    for (e in density.density.entries) {
                        tr {
                            key = e.key.toString()

                            td {
                                +e.key.start.toString()
                            }

                            td {
                                +e.key.endInclusive.toString()
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