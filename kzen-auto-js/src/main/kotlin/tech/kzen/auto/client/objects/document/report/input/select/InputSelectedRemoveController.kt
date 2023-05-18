package tech.kzen.auto.client.objects.document.report.input.select

import emotion.react.css
import js.core.jso
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import react.react
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.objects.document.report.input.select.model.InputSelectedState
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.RemoveCircleOutlineIcon
import tech.kzen.lib.common.model.locate.ObjectLocation
import web.cssom.Color
import web.cssom.Display
import web.cssom.NamedColor
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface InputSelectedRemoveControllerProps: react.Props {
    var mainLocation: ObjectLocation
    var disabled: Boolean
    var inputSelectedState: InputSelectedState
    var inputStore: ReportInputStore
}


//---------------------------------------------------------------------------------------------------------------------
class InputSelectedRemoveController(
    props: InputSelectedRemoveControllerProps
):
    RPureComponent<InputSelectedRemoveControllerProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private fun onRemoveFromSelection() {
        val removedPaths = props.inputSelectedState.selectedChecked
        props.inputStore.selected.selectionRemoveAsync(removedPaths)
    }


//    private fun checkedAlreadySelected(): List<DataLocation> {
//        if (props.inputState.selected.selectedChecked.isEmpty()) {
//            return listOf()
//        }
//
//        return props.inputState.selected.selectedChecked.filter { it in props.selectedDataLocation }
//    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val selectedRemoveCount = props.inputSelectedState.selectedChecked.size

        div {
            css {
                display = Display.inlineBlock
            }

            Button {
                variant = ButtonVariant.outlined
                size = Size.small

                sx {
//                    borderWidth = 2.px
                    color = NamedColor.black
                    borderColor = Color("#777777")
                }

                onClick = {
                    onRemoveFromSelection()
                }

                if (selectedRemoveCount == 0) {
                    disabled = true
                    title =
                        if (props.inputSelectedState.selectedChecked.isEmpty()) {
                            "No files selected"
                        }
                        else {
                            "No existing files selected"
                        }
                }
                else if (props.disabled) {
                    disabled = true
                    title = "Disabled while running"
                }

                RemoveCircleOutlineIcon::class.react {
                    style = jso {
                        marginRight = 0.25.em
                    }
                }

                if (selectedRemoveCount == 0) {
                    +"Remove"
                }
                else {
                    +"Remove ($selectedRemoveCount)"
                }
            }
        }
    }
}