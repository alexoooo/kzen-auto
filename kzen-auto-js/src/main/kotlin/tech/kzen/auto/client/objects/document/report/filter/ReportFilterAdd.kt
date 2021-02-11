package tech.kzen.auto.client.objects.document.report.filter

import kotlinx.browser.document
import kotlinx.css.*
import kotlinx.html.title
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.report.state.FilterAddRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.*
import kotlin.js.Json
import kotlin.js.json


class ReportFilterAdd(
    props: Props
):
    RPureComponent<ReportFilterAdd.Props, ReportFilterAdd.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
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
//            selectedColumn = null
        }
    }


    private fun onColumnSelected(columnName: String) {
        setState {
            selectedColumn = columnName
            adding = false
        }

        props.dispatcher.dispatchAsync(
            FilterAddRequest(columnName))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val availableColumns = props.reportState.inputAndCalculatedColumns()
            ?: return

        val filterSpec = props.reportState.filterSpec()
        val unusedOptions = availableColumns
            .filter { it !in filterSpec.columns }

        if (unusedOptions.isEmpty()) {
            return
        }

        val editDisabled =
            props.reportState.isInitiating()

        styledDiv {
            if (state.adding) {
                css {
                    marginTop = 0.5.em
                }
            }

            if (! props.reportState.filterLoading) {
                if (props.reportState.filterError != null) {
                    styledDiv {
                        +"Error: ${props.reportState.filterError}"
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
    }


    private fun RBuilder.renderAddButton() {
        styledDiv {
            attrs {
                title = "Add column filter"
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
                title = "Cancel adding column filter"
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


    private fun RBuilder.renderSelect(unusedOptions: List<String>, editDisabled: Boolean) {
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
}