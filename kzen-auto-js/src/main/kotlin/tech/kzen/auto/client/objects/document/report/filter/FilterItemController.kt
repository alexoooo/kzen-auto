package tech.kzen.auto.client.objects.document.report.filter

import kotlinx.css.*
import kotlinx.html.title
import react.RBuilder
import react.RPureComponent
import react.dom.*
import react.setState
import styled.*
import tech.kzen.auto.client.objects.document.common.edit.MultiTextAttributeEditor
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.filter.model.ReportFilterStore
import tech.kzen.auto.client.wrap.material.*
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.filter.ColumnFilterSpec
import tech.kzen.auto.common.objects.document.report.spec.filter.ColumnFilterType
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.common.objects.document.report.summary.*


class FilterItemController(
    props: Props
):
    RPureComponent<FilterItemController.Props, FilterItemController.State>(props)
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
    interface Props: react.Props {
        var filterSpec: FilterSpec
        var columnName: String
        var runningOrLoading: Boolean
        var inputAndCalculatedColumns: HeaderListing?
        var tableSummary: TableSummary?
        var filterStore: ReportFilterStore
    }


    interface State: react.State {
        var open: Boolean
        var removeError: String?
        var updateError: String?
    }


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

        props.filterStore.removeFilterAsync(props.columnName)
    }


    private fun onCriteriaChange(
        value: String,
        added: Boolean
    ) {
        if (added) {
            props.filterStore.addFilterValueAsync(props.columnName, value)
        }
        else {
            props.filterStore.removeFilterValueAsync(props.columnName, value)
        }
    }


    private fun onTypeChange(
        type: ColumnFilterType
    ) {
        props.filterStore.changeFilterTypeAsync(props.columnName, type)
    }


    private fun onChangedByEdit() {
        props.filterStore.changedByEdit()
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

        val inputAndCalculatedColumns = props.inputAndCalculatedColumns

        val missing =
            if (inputAndCalculatedColumns == null) {
                false
            }
            else {
                ! inputAndCalculatedColumns.values.contains(props.columnName)
            }

        val tableSummary = props.tableSummary
        val columnSummary = tableSummary?.columnSummaries?.get(props.columnName)

        val editDisabled = props.runningOrLoading

        styledDiv {
            css {
                borderTopWidth = ReportController.separatorWidth
                borderTopColor = ReportController.separatorColor
                borderTopStyle = BorderStyle.solid
            }

            val deleteDisabled = editDisabled && columnCriteria.values.isNotEmpty()
            renderHeader(columnSummary, missing, deleteDisabled)

            if (state.open || columnCriteria.values.isNotEmpty()) {
                styledDiv {
                    css {
                        marginLeft = 1.em
                        marginRight = 1.em
                    }

                    if (state.open) {
                        if (editDisabled) {
                            attrs {
                                title = "Disabled while filter running or loading"
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
        deleteDisabled: Boolean
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

                                    disabled = deleteDisabled
                                }

                                child(DeleteIcon::class) {}
                            }
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


    private fun RBuilder.renderSummary(columnFilterSpec: ColumnFilterSpec) {
        if (columnFilterSpec.values.isEmpty()) {
            return
        }

        val label =
            when (columnFilterSpec.type) {
                ColumnFilterType.RequireAny -> "Require"
                ColumnFilterType.ExcludeAll -> "Exclude"
            }

        styledDiv {
            css {
                maxHeight = 5.em
                overflowY = Overflow.auto
            }
            +"$label: "

            +columnFilterSpec.values.joinToString {
                it.ifBlank { "(blank)" }
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

        renderEditValues(columnFilterSpec, editDisabled)
    }


    private fun RBuilder.renderEditType(columnFilterSpec: ColumnFilterSpec, editDisabled: Boolean) {
//        +"type: ${columnCriteria.type}"

        child(MaterialToggleButtonGroup::class) {
            attrs {
                value = columnFilterSpec.type.name
                exclusive = true
//                size = "small"
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
                    size = "small"
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
                    size = "small"
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


    private fun RBuilder.renderEditValues(
        columnFilterSpec: ColumnFilterSpec,
        editDisabled: Boolean
    ) {
        child(MultiTextAttributeEditor::class) {
            attrs {
                labelOverride = "Filter values"
                disabled = editDisabled
                maxRows = 10

                objectLocation = props.filterStore.mainLocation()
                attributePath = FilterSpec.columnValuesAttributePath(props.columnName)

                value = columnFilterSpec.values
                unique = true

                onChange = {
                    onChangedByEdit()
                }
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
                maxHeight = 10.em
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