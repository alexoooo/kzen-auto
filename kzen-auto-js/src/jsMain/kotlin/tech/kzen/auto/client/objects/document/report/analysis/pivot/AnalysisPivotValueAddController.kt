package tech.kzen.auto.client.objects.document.report.analysis.pivot

import emotion.react.css
import js.objects.unsafeJso
import mui.material.IconButton
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.report.analysis.model.ReportAnalysisStore
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec
import web.cssom.Display
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface AnalysisPivotValueAddControllerProps: Props {
    var spec: PivotSpec
    var inputAndCalculatedColumns: HeaderListing?
    var analysisStore: ReportAnalysisStore
    var runningOrLoading: Boolean
}


external interface AnalysisPivotValueAddControllerState: State {
    var adding: Boolean
    var selectedColumn: HeaderLabel?
}


//---------------------------------------------------------------------------------------------------------------------
class AnalysisPivotValueAddController(
    props: AnalysisPivotValueAddControllerProps
):
    RPureComponent<AnalysisPivotValueAddControllerProps, AnalysisPivotValueAddControllerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun AnalysisPivotValueAddControllerState.init(props: AnalysisPivotValueAddControllerProps) {
        adding = false
        selectedColumn = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onAdd() {
        setState {
            adding = true
            selectedColumn = null
        }
    }


    private fun onCancel() {
        setState {
            adding = false
        }
    }


    private fun onColumnSelected(columnName: HeaderLabel) {
        setState {
            selectedColumn = columnName
        }

        async {
            props.analysisStore.addValue(columnName)

            setState {
                selectedColumn = null
                adding = false
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val columnListing = props.inputAndCalculatedColumns
            ?: return

        val unusedOptions = columnListing
            .values
            .filter { it !in props.spec.values.columns }

        if (unusedOptions.isEmpty()) {
            return
        }

        val editDisabled = props.runningOrLoading

        div {
            if (state.adding) {
                css {
                    marginTop = 0.5.em
                }
            }

            if (state.adding) {
                div {
                    css {
                        display = Display.inlineBlock
                        width = 15.em
                    }

                    renderSelect(unusedOptions, editDisabled)
                }

                renderCancelButton()
            }
            else {
                renderAddButton()
            }
        }
    }


    private fun ChildrenBuilder.renderSelect(unusedOptions: List<HeaderLabel>, editDisabled: Boolean) {
        val selectOptions = unusedOptions
            .map {
                val option: SelectOption = unsafeJso {
                    value = it.asString()
                    label = it.render()
                }
                option
            }
            .toTypedArray()

        muiAutocompleteField(
            label = "Column name",
            options = selectOptions,
            selectedOption = selectOptions.find { HeaderLabel.ofString(it.value) == state.selectedColumn },
            onSelect = { onColumnSelected(HeaderLabel.ofString(it.value)) },
            disabled = editDisabled,
            disableClearable = true,
            autoFocus = true,
            openOnFocus = true)
    }


    private fun ChildrenBuilder.renderAddButton() {
        div {
            title = "Add pivot value"
            css {
                display = Display.inlineBlock
            }

            IconButton {
                onClick = {
                    onAdd()
                }

                icon("material-symbols:add-circle-outline") {}
            }
        }
    }


    private fun ChildrenBuilder.renderCancelButton() {
        div {
            title = "Cancel adding pivot value"
            css {
                display = Display.inlineBlock
            }

            IconButton {
                onClick = {
                    onCancel()
                }

                icon("material-symbols:cancel") {}
            }
        }
    }
}