package tech.kzen.auto.client.objects.document.report.pivot

import kotlinx.browser.document
import kotlinx.css.*
import kotlinx.html.title
import react.*
import react.dom.attrs
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.report.state.PivotValueAddRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.client.wrap.material.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.material.CancelIcon
import tech.kzen.auto.client.wrap.material.MaterialIconButton
import tech.kzen.auto.client.wrap.material.MaterialInputLabel
import tech.kzen.auto.client.wrap.select.ReactSelect
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec
import kotlin.js.Json
import kotlin.js.json


class PivotValueAdd(
    props: Props
):
    RPureComponent<PivotValueAdd.Props, PivotValueAdd.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var pivotSpec: PivotSpec,
        var reportState: ReportState,
        var dispatcher: ReportDispatcher
    ): RProps

    class State(
        var adding: Boolean,
        var selectedColumn: String?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
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
            props.dispatcher.dispatch(
                PivotValueAddRequest(columnName))

            setState {
                selectedColumn = null
                adding = false
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val columnListing = props.reportState.inputAndCalculatedColumns()
            ?: return

        val unusedOptions = columnListing
            .values
            .filter { it !in props.pivotSpec.values.columns }

        if (unusedOptions.isEmpty()) {
            return
        }

        val editDisabled =
            props.reportState.isInitiating() ||
            props.reportState.taskRunning ||
            props.reportState.pivotLoading

        styledDiv {
            if (state.adding) {
                css {
                    marginTop = 0.5.em
                }
            }

            if (state.adding) {
                styledDiv {
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


    fun RBuilder.renderSelect(unusedOptions: List<String>, editDisabled: Boolean) {
        val selectId = "material-react-select-id"

        child(MaterialInputLabel::class) {
            attrs {
                htmlFor = selectId

                style = reactStyle {
                    fontSize = 0.8.em
                }
            }

            +"Column name"
        }

        val selectOptions = unusedOptions
            .map { ReactSelectOption(it, it) }
            .toTypedArray()

        child(ReactSelect::class) {
            attrs {
                id = selectId
//                placeholder = "Add value"

                value = selectOptions.find { it.value == state.selectedColumn }
//                value = firstOption

                options = selectOptions
//                options = optionsArray

                onChange = {
//                    console.log("^^^^^ selected: $it")
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
    }


    private fun RBuilder.renderAddButton() {
        styledDiv {
            attrs {
                title = "Add pivot value"
            }

            css {
                display = Display.inlineBlock
            }

            child(MaterialIconButton::class) {
                attrs {
                    onClick = {
                        onAdd()
                    }
                }

                child(AddCircleOutlineIcon::class) {}
            }
        }
    }


    private fun RBuilder.renderCancelButton() {
        styledDiv {
            attrs {
                title = "Cancel adding pivot value"
            }

            css {
                display = Display.inlineBlock
            }

            child(MaterialIconButton::class) {
                attrs {
                    onClick = {
                        onCancel()
                    }
                }

                child(CancelIcon::class) {}
            }
        }
    }
}