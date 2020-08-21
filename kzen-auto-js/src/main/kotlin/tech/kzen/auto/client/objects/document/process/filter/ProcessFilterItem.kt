package tech.kzen.auto.client.objects.document.process.filter

import kotlinx.css.*
import react.*
import react.dom.*
import styled.*
import tech.kzen.auto.client.objects.document.common.AttributePathValueEditor
import tech.kzen.auto.client.objects.document.process.state.*
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.filter.CriteriaSpec
import tech.kzen.auto.common.objects.document.filter.FilterConventions
import tech.kzen.auto.common.paradigm.reactive.ColumnSummary
import tech.kzen.auto.common.paradigm.reactive.NominalValueSummary
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassNames
import tech.kzen.lib.platform.collect.persistentListOf


class ProcessFilterItem(
    props: Props
):
    RPureComponent<ProcessFilterItem.Props, ProcessFilterItem.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val maxValueLength = 96
        private const val abbreviationSuffix = "..."


        private fun formatCount(count: Long): String {
            return count.toString()
                .replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")
        }


        private fun abbreviateValue(value: String): String {
            if (value.isBlank()) {
                return "$value(blank)"
            }

            if (value.length < maxValueLength) {
                return value
            }

            val truncated = value.substring(0, maxValueLength - abbreviationSuffix.length)
            return truncated + abbreviationSuffix
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var processState: ProcessState,
        var dispatcher: ProcessDispatcher,
        var criteriaSpec: CriteriaSpec,
        var columnName: String
    ): RProps


    class State(
        var open: Boolean,
        var removeError: String?,
        var updateError: String?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        open = false
        removeError = null
        updateError = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onDelete() {
        if (state.removeError != null) {
            setState {
                updateError = null
            }
        }

        async {
            val effect = props.dispatcher.dispatch(
                FilterRemoveRequest(props.columnName)
            ).single() as FilterUpdateResult

            if (effect.errorMessage != null) {
                setState {
                    removeError = effect.errorMessage
                }
            }
        }
    }


    private fun onCriteriaChange(
        value: String,
        added: Boolean
    ) {
        val request =
            if (added) {
                FilterValueAddRequest(props.columnName, value)
            }
            else {
                FilterValueRemoveRequest(props.columnName, value)
            }

        if (state.updateError != null) {
            setState {
                updateError = null
            }
        }

        async {
            val effect = props.dispatcher.dispatch(
                request
            ).single() as FilterUpdateResult

            if (effect.errorMessage != null) {
                setState {
                    updateError = effect.errorMessage
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onOpenToggle() {
        setState {
            open = ! open
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val requiredValues = props.criteriaSpec.columnRequiredValues[props.columnName]
            ?: return

        val columnSummary = props.processState.tableSummary?.columnSummaries?.get(props.columnName)

        styledDiv {
            css {
                borderTopWidth = 1.px
                borderTopStyle = BorderStyle.solid
                borderTopColor = Color.lightGray
            }

            renderHeader(columnSummary)

            if (state.open || requiredValues.isNotEmpty()) {
                styledDiv {
                    css {
                        marginLeft = 1.em
                        marginRight = 1.em
                    }

                    if (state.open) {
                        renderDetail(requiredValues, columnSummary)
                    }
                    else {
                        renderSummary(requiredValues)
                    }
                }
            }
        }
    }


    private fun RBuilder.renderHeader(columnSummary: ColumnSummary?) {
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
                                fontSize = 1.5.em
                            }

                            +props.columnName
                        }
                    }

                    styledTd {
                        css {
                            width = 20.em
                            textAlign = TextAlign.right
                        }

                        if (columnSummary != null) {
                            val countFormat = formatCount(columnSummary.count)
                            +"Count: $countFormat"
                        }

                        span {
                            val removeError = state.removeError
                            if (removeError != null) {
                                +removeError
                            }

                            child(MaterialIconButton::class) {
                                attrs {
                                    onClick = {
                                        onDelete()
                                    }
                                }

                                child(DeleteIcon::class) {}
                            }
                        }

                        child(MaterialIconButton::class) {
                            attrs {
                                onClick = {
                                    onOpenToggle()
                                }

//                                disabled = (props.columnSummary?.isEmpty() ?: true)
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


    private fun RBuilder.renderSummary(requiredValues: Set<String>) {
//        console.log("requiredValues: $requiredValues")

        +requiredValues.joinToString {
            if (it.isBlank()) {
                "(blank)"
            }
            else {
                it
            }
        }
    }


    private fun RBuilder.renderDetail(requiredValues: Set<String>, columnSummary: ColumnSummary?) {
        renderEditValues()

        if (columnSummary == null) {
            return
        }

        val hasNominal = ! columnSummary.nominalValueSummary.isEmpty()
        if (hasNominal) {
            renderHistogram(requiredValues, columnSummary.nominalValueSummary)
        }
    }


    private fun RBuilder.renderHistogram(requiredValues: Set<String>, histogram: NominalValueSummary) {
        styledDiv {
            css {
                maxHeight = 20.em
                overflowY = Overflow.auto
                marginTop = 0.5.em
            }

            if (state.updateError != null) {
                +"Error: ${state.updateError}"
            }

            table {
                styledThead {
                    tr {
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
//                                backgroundColor = Color.white
                                backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                zIndex = 999
                            }
                            +"Filter"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
//                                backgroundColor = Color.white
                                backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                zIndex = 999
                            }
                            +"Value"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
//                                backgroundColor = Color.white
                                backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                zIndex = 999
                            }
                            +"Count"
                        }
                    }
                }

                tbody {
                    for (e in histogram.histogram.entries) {
                        val checked = requiredValues.contains(e.key)

                        tr {
                            key = e.key

                            td {
                                child(MaterialCheckbox::class) {
                                    attrs {
                                        this.checked = checked
//                                        this.disabled = props.filterRunning

                                        onChange = {
                                            onCriteriaChange(e.key, ! checked)
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


    private fun RBuilder.renderEditValues() {
        child(AttributePathValueEditor::class) {
            attrs {
                labelOverride = "Filter values"

                clientState = props.processState.clientState
                objectLocation = props.processState.mainLocation

                attributePath = AttributePath(
                    FilterConventions.criteriaAttributeName,
                    AttributeNesting(persistentListOf(AttributeSegment.ofKey(props.columnName))))

                valueType = TypeMetadata(
                    ClassNames.kotlinSet,
                    listOf(TypeMetadata(
                        ClassNames.kotlinString, listOf())))
            }
        }
    }
}