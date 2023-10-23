package tech.kzen.auto.client.objects.document.report.filter

import emotion.react.css
import mui.material.*
import mui.material.Size
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.react
import tech.kzen.auto.client.objects.document.common.edit.MultiTextAttributeEditor
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.filter.model.ReportFilterStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.DeleteIcon
import tech.kzen.auto.client.wrap.material.ExpandLessIcon
import tech.kzen.auto.client.wrap.material.ExpandMoreIcon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.filter.ColumnFilterSpec
import tech.kzen.auto.common.objects.document.report.spec.filter.ColumnFilterType
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.common.objects.document.report.summary.*
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface FilterItemControllerProps: react.Props {
    var filterSpec: FilterSpec
    var columnName: String
    var runningOrLoading: Boolean
    var inputAndCalculatedColumns: HeaderListing?
    var tableSummary: TableSummary?
    var filterStore: ReportFilterStore
}


external interface FilterItemControllerState: react.State {
    var open: Boolean
    var removeError: String?
    var updateError: String?
}


//---------------------------------------------------------------------------------------------------------------------
class FilterItemController(
    props: FilterItemControllerProps
):
    RPureComponent<FilterItemControllerProps, FilterItemControllerState>(props)
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
    override fun FilterItemControllerState.init(props: FilterItemControllerProps) {
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
        val toggle = ! state.open
        setState {
            open = toggle
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
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

        div {
            css {
                borderTopWidth = ReportController.separatorWidth
                borderTopColor = ReportController.separatorColor
                borderTopStyle = LineStyle.solid
            }

            val deleteDisabled = editDisabled && columnCriteria.values.isNotEmpty()
            renderHeader(columnSummary, missing, deleteDisabled)

            if (state.open || columnCriteria.values.isNotEmpty()) {
                div {
                    css {
                        marginLeft = 1.em
                        marginRight = 1.em
                    }

                    if (state.open) {
                        if (editDisabled) {
                            title = "Disabled while filter running or loading"
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


    private fun ChildrenBuilder.renderHeader(
        columnSummary: ColumnSummary?,
        missing: Boolean,
        deleteDisabled: Boolean
    ) {
        table {
            css {
                width = 100.pct
            }

            tbody {
                tr {
                    td {
                        css {
                            width = 100.pct.minus(20.em)
                        }

                        span {
                            css {
                                fontSize = 1.5.em

                                if (missing) {
                                    color = NamedColor.gray
                                }
                            }

                            +props.columnName
                        }

                        if (missing) {
                            span {
                                css {
                                    color = NamedColor.gray
                                    marginLeft = 0.5.em
                                }
                                +"(missing in Input)"
                            }
                        }
                    }

                    td {
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

                            IconButton {
                                onClick = {
                                    onDelete()
                                }

                                disabled = deleteDisabled

                                DeleteIcon::class.react {}
                            }
                        }

                        IconButton {
                            onClick = {
                                onOpenToggle()
                            }

                            if (state.open) {
                                ExpandLessIcon::class.react {}
                            }
                            else {
                                ExpandMoreIcon::class.react {}
                            }
                        }
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderSummary(columnFilterSpec: ColumnFilterSpec) {
        if (columnFilterSpec.values.isEmpty()) {
            return
        }

        val label =
            when (columnFilterSpec.type) {
                ColumnFilterType.RequireAny -> "Require"
                ColumnFilterType.ExcludeAll -> "Exclude"
            }

        div {
            css {
                maxHeight = 5.em
                overflowY = Auto.auto
            }
            +"$label: "

            +columnFilterSpec.values.joinToString {
                it.ifBlank { "(blank)" }
            }
        }
    }


    private fun ChildrenBuilder.renderDetail(
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


    private fun ChildrenBuilder.renderEditCriteria(columnFilterSpec: ColumnFilterSpec, editDisabled: Boolean) {
        if (state.updateError != null) {
            +"Error: ${state.updateError}"
        }

        div {
            css {
                marginBottom = 0.5.em
            }
            renderEditType(columnFilterSpec, editDisabled)
        }

        renderEditValues(columnFilterSpec, editDisabled)
    }


    private fun ChildrenBuilder.renderEditType(columnFilterSpec: ColumnFilterSpec, editDisabled: Boolean) {
//        +"type: ${columnCriteria.type}"

        ToggleButtonGroup {
            value = columnFilterSpec.type.name
            exclusive = true
//                size = "small"
            onChange = { _, v ->
                if (v is String) {
                    onTypeChange(ColumnFilterType.valueOf(v))
                }
            }

            ToggleButton {
                value = ColumnFilterType.RequireAny.name
                disabled = editDisabled
                size = Size.small

                span {
                    css {
                        fontWeight = FontWeight.bold
                    }
                    +"Require any"
                }
            }

            ToggleButton {
                value = ColumnFilterType.ExcludeAll.name
                disabled = editDisabled
                size = Size.small

                span {
                    css {
                        fontWeight = FontWeight.bold
                    }
                    +"Exclude all"
                }
            }
        }
    }


    private fun ChildrenBuilder.renderEditValues(
        columnFilterSpec: ColumnFilterSpec,
        editDisabled: Boolean
    ) {
        MultiTextAttributeEditor::class.react {
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


    private fun ChildrenBuilder.renderHistogram(
        columnFilterSpec: ColumnFilterSpec,
        histogram: NominalValueSummary,
        editDisabled: Boolean
    ) {
        div {
            css {
                maxHeight = 20.em
                overflowY = Auto.auto
                marginTop = 0.5.em
            }

            table {
                thead {
                    tr {
                        th {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                zIndex = integer(999)
                            }
                            +"Filter"
                        }
                        th {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                zIndex = integer(999)
                            }
                            +"Value"
                        }
                        th {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                zIndex = integer(999)
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
                                Checkbox {
                                    this.checked = checked
                                    disabled = editDisabled

                                    onChange = { _, _ ->
                                        onCriteriaChange(e.key, ! checked)
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


    private fun ChildrenBuilder.renderNumeric(density: StatisticValueSummary) {
        div {
            css {
                maxHeight = 20.em
                overflowY = Auto.auto
                marginTop = 0.5.em
            }

            table {
                thead {
                    tr {
                        th {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = NamedColor.white
                            }
                            +"Statistic"
                        }
                        th {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = NamedColor.white
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


    private fun ChildrenBuilder.renderSample(opaque: OpaqueValueSummary) {
        div {
            css {
                maxHeight = 10.em
                overflowY = Auto.auto
                marginTop = 0.5.em
            }

            table {
                thead {
                    tr {
                        th {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = NamedColor.white
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