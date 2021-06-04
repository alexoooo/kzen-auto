package tech.kzen.auto.client.objects.document.report.formula

import kotlinx.browser.document
import kotlinx.css.*
import kotlinx.html.title
import react.*
import react.dom.attrs
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.wrap.*
import kotlin.js.Json
import kotlin.js.json


class FormulaColumnReference(
    props: Props
):
    RPureComponent<FormulaColumnReference.Props, FormulaColumnReference.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var columnNames: List<String>
        var editDisabled: Boolean
        var onAdded: (String) -> Unit
        var addLabel: String
        var addIcon: String
    }


    interface State: RState {
        var adding: Boolean
        var selectedColumn: String?
    }


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


    private fun onValueChange(columnName: String) {
        props.onAdded.invoke(columnName)

        setState {
            selectedColumn = columnName
            adding = false
            selectedColumn = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
//                backgroundColor = Color.green
                if (state.adding) {
                    marginBottom = -(1).em
                    marginLeft = 0.5.em
                }

                whiteSpace = WhiteSpace.nowrap
            }

            if (state.adding) {
                styledDiv {
                    css {
                        display = Display.inlineBlock
                        width = 15.em
                    }

                    renderSelect()
                }

                renderCancel()
            }
            else {
                renderAddButton()
            }
        }
    }


    private fun RBuilder.renderAddButton() {
        styledDiv {
            attrs {
                title = props.addLabel
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

                iconByName(props.addIcon)
            }
        }
    }


    private fun RBuilder.renderCancel() {
        styledDiv {
            css {
                display = Display.inlineBlock
            }

//            child(MaterialIconButton::class) {
//                attrs {
//                    onClick = {
//                        onSubmit()
//                    }
//                }
//
//                child(AddIcon::class) {}
//            }

            child(MaterialIconButton::class) {
                attrs {
                    title = "Cancel"
                    onClick = {
                        onCancel()
                    }
//                    disabled = props.reportState.formulaLoading
                }

                child(CancelIcon::class) {}
            }
        }
    }


    private fun RBuilder.renderSelect() {
        val selectId = "material-react-select-id"

        child(MaterialInputLabel::class) {
            attrs {
                htmlFor = selectId

                style = reactStyle {
                    fontSize = 0.8.em
                }
            }

            +"Column"
        }

        val selectOptions = props
            .columnNames
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
                    onValueChange(it.value)
                }

                isDisabled = props.editDisabled

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