package tech.kzen.auto.client.objects.document.report.analysis.pivot

import web.cssom.Display
import web.cssom.em
import emotion.react.css
import js.core.jso
import kotlinx.browser.document
import mui.material.IconButton
import mui.material.InputLabel
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.react
import tech.kzen.auto.client.objects.document.report.analysis.model.ReportAnalysisStore
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.material.CancelIcon
import tech.kzen.auto.client.wrap.select.ReactSelect
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec
import kotlin.js.Json
import kotlin.js.json


//---------------------------------------------------------------------------------------------------------------------
external interface AnalysisPivotValueAddControllerProps: Props {
    var spec: PivotSpec
    var inputAndCalculatedColumns: HeaderListing?
    var analysisStore: ReportAnalysisStore
    var runningOrLoading: Boolean
}


external interface AnalysisPivotValueAddControllerState: State {
    var adding: Boolean
    var selectedColumn: String?
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


    private fun onColumnSelected(columnName: String) {
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


    fun ChildrenBuilder.renderSelect(unusedOptions: List<String>, editDisabled: Boolean) {
        val selectId = "material-react-select-id"

        InputLabel {
            htmlFor = selectId

            css {
                fontSize = 0.8.em
            }

            +"Column name"
        }

        val selectOptions = unusedOptions
            .map {
                val option: ReactSelectOption = jso {
                    value = it
                    label = it
                }
                option
            }
            .toTypedArray()

        ReactSelect::class.react {
            id = selectId

            value = selectOptions.find { it.value == state.selectedColumn }

            options = selectOptions

            onChange = {
                onColumnSelected(it.value)
            }

            isDisabled = editDisabled

            // https://stackoverflow.com/a/51844542/1941359
            val styleTransformer: (Json, Json) -> Json = { base, _ ->
                val transformed = json()
                transformed.add(base)
                transformed["background"] = "transparent"
                transformed
            }

            val reactStyles = json()
            reactStyles["control"] = styleTransformer
            styles = reactStyles

            // NB: this was causing clipping when used in ConditionalStepDisplay table,
            //   see: https://react-select.com/advanced#portaling
            menuPortalTarget = document.body!!
        }
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

                AddCircleOutlineIcon::class.react {}
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

                CancelIcon::class.react {}
            }
        }
    }
}