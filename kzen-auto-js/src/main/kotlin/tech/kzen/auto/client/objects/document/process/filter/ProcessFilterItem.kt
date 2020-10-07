package tech.kzen.auto.client.objects.document.process.filter

import kotlinx.css.*
import kotlinx.html.title
import react.*
import react.dom.*
import styled.*
import tech.kzen.auto.client.objects.document.common.AttributePathValueEditor
import tech.kzen.auto.client.objects.document.process.state.*
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.process.ColumnFilterSpec
import tech.kzen.auto.common.objects.document.process.ColumnFilterType
import tech.kzen.auto.common.objects.document.process.FilterSpec
import tech.kzen.auto.common.paradigm.reactive.ColumnSummary
import tech.kzen.auto.common.paradigm.reactive.NominalValueSummary
import tech.kzen.auto.common.paradigm.reactive.OpaqueValueSummary
import tech.kzen.auto.common.paradigm.reactive.StatisticValueSummary
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassNames


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
        var filterSpec: FilterSpec,
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
            val actions = props.dispatcher.dispatch(request)
//            console.log("^^ - $request - $actions")

            val effect = actions.single() as FilterUpdateResult

            if (effect.errorMessage != null) {
                setState {
                    updateError = effect.errorMessage
                }
            }
        }
    }


    private fun onTypeChange(
        type: ColumnFilterType
    ) {
        async {
            val actions = props.dispatcher.dispatch(
                FilterTypeChangeRequest(props.columnName, type))

            val effect = actions.single() as FilterUpdateResult

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
        val columnCriteria = props.filterSpec.columns[props.columnName]
            ?: return

        val processState = props.processState

        val missing =
            if (processState.columnListing == null) {
                false
            }
            else {
                ! processState.columnListing.contains(props.columnName)
            }

        val columnSummary = processState.tableSummary?.columnSummaries?.get(props.columnName)

        val editDisabled =
            props.processState.initiating ||
            props.processState.filterTaskRunning

        styledDiv {
            css {
                borderTopWidth = 1.px
                borderTopStyle = BorderStyle.solid
                borderTopColor = Color.lightGray
            }

            renderHeader(columnSummary, missing, editDisabled)

            if (state.open || columnCriteria.values.isNotEmpty()) {
                styledDiv {
                    css {
                        marginLeft = 1.em
                        marginRight = 1.em
                    }

                    if (state.open) {
                        if (editDisabled) {
                            attrs {
                                title =
                                    if (props.processState.initiating) {
                                        "Disabled while loading"
                                    }
                                    else {
                                        "Disabled while filter running"
                                    }
                            }
                        }

                        renderDetail(columnCriteria, columnSummary, editDisabled)
                    }
                    else {
                        renderSummary(columnCriteria)
                    }
                }
            }
        }
    }


    private fun RBuilder.renderHeader(
        columnSummary: ColumnSummary?,
        missing: Boolean,
        editDisabled: Boolean
    ) {
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

                                if (missing) {
                                    color = Color.gray
                                }
                            }

                            +props.columnName
                        }

                        if (missing) {
                            styledSpan {
                                css {
                                    color = Color.gray
                                    marginLeft = 0.5.em
                                }
                                +"(missing in Input)"
                            }
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

                        styledSpan {
                            val removeError = state.removeError
                            if (removeError != null) {
                                +removeError
                            }

                            child(MaterialIconButton::class) {
                                attrs {
                                    onClick = {
                                        onDelete()
                                    }

                                    disabled = editDisabled
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


    private fun RBuilder.renderSummary(columnFilterSpec: ColumnFilterSpec) {
        if (columnFilterSpec.values.isEmpty()) {
            return
        }

        val label =
            when (columnFilterSpec.type) {
                ColumnFilterType.RequireAny -> "Require"
                ColumnFilterType.ExcludeAll -> "Exclude"
            }

        +"$label: "

        +columnFilterSpec.values.joinToString {
            if (it.isBlank()) {
                "(blank)"
            }
            else {
                it
            }
        }
    }


    private fun RBuilder.renderDetail(
        columnFilterSpec: ColumnFilterSpec,
        columnSummary: ColumnSummary?,
        editDisabled: Boolean
    ) {
        renderEditCriteria(columnFilterSpec, editDisabled)

        if (columnSummary == null) {
            return
        }

        val hasNominal = ! columnSummary.nominalValueSummary.isEmpty()
        if (hasNominal) {
            renderHistogram(columnFilterSpec, columnSummary.nominalValueSummary, editDisabled)
        }

        val hasNumeric = ! columnSummary.numericValueSummary.isEmpty()
        if (hasNumeric) {
            renderNumeric(columnSummary.numericValueSummary)
        }

        val hasSample = ! columnSummary.opaqueValueSummary.isEmpty()
        if (hasSample) {
            renderSample(columnSummary.opaqueValueSummary)
        }
    }


    private fun RBuilder.renderEditCriteria(columnFilterSpec: ColumnFilterSpec, editDisabled: Boolean) {
        if (state.updateError != null) {
            +"Error: ${state.updateError}"
        }

        styledDiv {
            css {
                marginBottom = 0.5.em
            }
            renderEditType(columnFilterSpec, editDisabled)
        }

        renderEditValues(editDisabled)
    }


    private fun RBuilder.renderEditType(columnFilterSpec: ColumnFilterSpec, editDisabled: Boolean) {
//        +"type: ${columnCriteria.type}"

        child(MaterialToggleButtonGroup::class) {
            attrs {
                value = columnFilterSpec.type.name
                exclusive = true
                onChange = { _, v ->
                    if (v is String) {
                        onTypeChange(ColumnFilterType.valueOf(v))
                    }
                }
            }

            child(MaterialToggleButton::class) {
                attrs {
                    value = ColumnFilterType.RequireAny.name
                    disabled = editDisabled
                }
                styledSpan {
                    css {
                        fontWeight = FontWeight.bold
                    }
                    +"Require any"
                }
            }

            child(MaterialToggleButton::class) {
                attrs {
                    value = ColumnFilterType.ExcludeAll.name
                    disabled = editDisabled
                }
                styledSpan {
                    css {
                        fontWeight = FontWeight.bold
                    }
                    +"Exclude all"
                }
            }
        }
    }


    private fun RBuilder.renderEditValues(editDisabled: Boolean) {
        child(AttributePathValueEditor::class) {
            attrs {
                labelOverride = "Filter values"
                disabled = editDisabled

                clientState = props.processState.clientState
                objectLocation = props.processState.mainLocation

                attributePath = FilterSpec.columnValuesAttributePath(props.columnName)

                valueType = TypeMetadata(
                    ClassNames.kotlinSet,
                    listOf(TypeMetadata(
                        ClassNames.kotlinString, listOf())))
            }
        }
    }


    private fun RBuilder.renderHistogram(
        columnFilterSpec: ColumnFilterSpec,
        histogram: NominalValueSummary,
        editDisabled: Boolean
    ) {
        styledDiv {
            css {
                maxHeight = 20.em
                overflowY = Overflow.auto
                marginTop = 0.5.em
            }

            table {
                styledThead {
                    tr {
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                zIndex = 999
                            }
                            +"Filter"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                zIndex = 999
                            }
                            +"Value"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                zIndex = 999
                            }
                            +"Count"
                        }
                    }
                }

                tbody {
                    for (e in histogram.histogram.entries) {
                        val checked = columnFilterSpec.values.contains(e.key)

                        tr {
                            key = e.key

                            td {
                                child(MaterialCheckbox::class) {
                                    attrs {
                                        this.checked = checked
                                        disabled = editDisabled

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


    private fun RBuilder.renderNumeric(density: StatisticValueSummary) {
        styledDiv {
            css {
                maxHeight = 20.em
                overflowY = Overflow.auto
                marginTop = 0.5.em
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
                }
            }
        }
    }


    private fun RBuilder.renderSample(opaque: OpaqueValueSummary) {
        styledDiv {
            css {
                maxHeight = 20.em
                overflowY = Overflow.auto
                marginTop = 0.5.em
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