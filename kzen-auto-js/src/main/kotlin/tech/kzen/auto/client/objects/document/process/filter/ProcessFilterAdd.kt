package tech.kzen.auto.client.objects.document.process.filter

import kotlinx.css.*
import kotlinx.html.title
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.process.state.FilterAddRequest
import tech.kzen.auto.client.objects.document.process.state.ProcessDispatcher
import tech.kzen.auto.client.objects.document.process.state.ProcessState
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.filter.CriteriaSpec
import kotlin.browser.document
import kotlin.js.Json
import kotlin.js.json


class ProcessFilterAdd(
    props: Props
):
    RPureComponent<ProcessFilterAdd.Props, ProcessFilterAdd.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var processState: ProcessState,
        var dispatcher: ProcessDispatcher,
        var criteriaSpec: CriteriaSpec
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
        val columnListing = props.processState.columnListing
            ?: return

        styledDiv {
            if (state.adding) {
                css {
                    marginTop = 0.5.em
                }
            }

            if (props.processState.filterAddLoading) {
                +"Adding..."
            }
            else {
                if (props.processState.filterAddError != null) {
                    styledDiv {
                        +"Error: ${props.processState.filterAddError}"
                    }
                }

                if (state.adding) {
                    styledDiv {
                        css {
                            display = Display.inlineBlock
                            width = 15.em
                        }

                        renderSelect(columnListing)
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


    private fun RBuilder.renderSelect(columnListing: List<String>) {
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

        val selectOptions = columnListing
            .filter { it !in props.criteriaSpec.columnRequiredValues }
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