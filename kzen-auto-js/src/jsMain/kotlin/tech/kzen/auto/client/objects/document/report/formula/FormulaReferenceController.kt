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
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import web.cssom.Display
import web.cssom.WhiteSpace
import web.cssom.em
import kotlin.js.Json
import kotlin.js.json


//---------------------------------------------------------------------------------------------------------------------
external interface FormulaReferenceControllerProps: react.Props {
    var inputColumns: HeaderListing
    var editDisabled: Boolean
    var onAdded: (HeaderLabel) -> Unit
    var addLabel: String
    var addIcon: String
}


external interface FormulaReferenceControllerState: react.State {
    var adding: Boolean
    var selectedColumn: HeaderLabel?
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


    private fun onValueChange(columnName: HeaderLabel) {
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
        val selectOptions = props
            .inputColumns
            .values
            .map {
                val option: ReactSelectOption = jso {
                    value = it.asString()
                    label = it.render()
                }
                option
            }
            .toTypedArray()

        InputLabel {
            css {
                fontSize = 0.8.em
            }

            +"Column"

            ReactSelect::class.react {
                value = selectOptions.find { HeaderLabel.ofString(it.value) == state.selectedColumn }
                options = selectOptions

                onChange = {
                    onValueChange(HeaderLabel.ofString(it.value))
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