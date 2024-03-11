package tech.kzen.auto.client.objects.document.report.formula

import emotion.react.css
import js.objects.jso
import kotlinx.browser.document
import mui.material.IconButton
import mui.material.InputLabel
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import react.react
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.CancelIcon
import tech.kzen.auto.client.wrap.material.iconByName
import tech.kzen.auto.client.wrap.select.ReactSelect
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.client.wrap.setState
import web.cssom.Display
import web.cssom.WhiteSpace
import web.cssom.em
import kotlin.js.Json
import kotlin.js.json


//---------------------------------------------------------------------------------------------------------------------
external interface FormulaReferenceControllerProps: react.Props {
    var inputColumns: List<String>
    var editDisabled: Boolean
    var onAdded: (String) -> Unit
    var addLabel: String
    var addIcon: String
}


external interface FormulaReferenceControllerState: react.State {
    var adding: Boolean
    var selectedColumn: String?
}


//---------------------------------------------------------------------------------------------------------------------
class FormulaReferenceController(
    props: FormulaReferenceControllerProps
):
    RPureComponent<FormulaReferenceControllerProps, FormulaReferenceControllerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun FormulaReferenceControllerState.init(props: FormulaReferenceControllerProps) {
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
    override fun ChildrenBuilder.render() {
        div {
            css {
                if (state.adding) {
                    marginBottom = (-1).em
                    marginLeft = 0.5.em
                }

                whiteSpace = WhiteSpace.nowrap
            }

            if (state.adding) {
                div {
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


    private fun ChildrenBuilder.renderAddButton() {
        div {
            title = props.addLabel

            css {
                display = Display.inlineBlock
            }

            IconButton {
                onClick = {
                    onAdd()
                }

                iconByName(props.addIcon)
            }
        }
    }


    private fun ChildrenBuilder.renderCancel() {
        div {
            css {
                display = Display.inlineBlock
            }

            IconButton {
                title = "Cancel"
                onClick = {
                    onCancel()
                }
                CancelIcon::class.react {}
            }
        }
    }


    private fun ChildrenBuilder.renderSelect() {
        val selectId = "material-react-select-id"

        InputLabel {
            htmlFor = selectId

            css {
                fontSize = 0.8.em
            }

            +"Column"
        }

        val selectOptions = props
            .inputColumns
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