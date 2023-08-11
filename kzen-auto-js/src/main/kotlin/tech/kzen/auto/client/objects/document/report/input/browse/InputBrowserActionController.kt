package tech.kzen.auto.client.objects.document.report.input.browse

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
import tech.kzen.auto.client.objects.document.report.input.browse.model.InputBrowserState
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.material.RemoveCircleOutlineIcon
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.Color
import web.cssom.Display
import web.cssom.NamedColor
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface InputBrowserActionControllerProps: react.Props {
    var mainLocation: ObjectLocation
    var dataLocationInfos: List<DataLocationInfo>
    var selectedDataLocation: Set<DataLocation>
    var inputBrowserState: InputBrowserState
    var inputStore: ReportInputStore
}


//---------------------------------------------------------------------------------------------------------------------
class InputBrowserActionController(
    props: InputBrowserActionControllerProps
):
    RPureComponent<InputBrowserActionControllerProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private fun onAddToSelection() {
        val addedPaths = checkedNotSelected()
        props.inputStore.selected.selectionAddAsync(addedPaths)
    }


    private fun onRemoveFromSelection() {
        val removedPaths = checkedAlreadySelected()
        props.inputStore.selected.selectionRemoveAsync(removedPaths)
    }


    private fun checkedNotSelected(): List<DataLocation> {
        if (props.inputBrowserState.browserChecked.isEmpty()) {
            return listOf()
        }

        return props.inputBrowserState.browserChecked.filter { it !in props.selectedDataLocation }
    }


    private fun checkedAlreadySelected(): List<DataLocation> {
        if (props.inputBrowserState.browserChecked.isEmpty()) {
            return listOf()
        }

        return props.inputBrowserState.browserChecked.filter { it in props.selectedDataLocation }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val selectedAddCount = checkedNotSelected().size
        val selectedRemoveCount = checkedAlreadySelected().size

        div {
            css {
                minWidth = 8.em
                display = Display.inlineBlock
            }

            Button {
                variant = ButtonVariant.outlined
                size = Size.small

                sx {
                    marginRight = 1.em
//                    borderWidth = 2.px
                    color = NamedColor.black
                    borderColor = Color("#777777")
                }

                onClick = {
                    onAddToSelection()
                }

                if (selectedAddCount == 0) {
                    disabled = true
                    title =
                        if (props.inputBrowserState.browserChecked.isEmpty()) {
                            "No files selected"
                        }
                        else {
                            "No new files selected"
                        }
                }
                else if (props.inputBrowserState.browserInfoLoading) {
                    disabled = true
                    title = "Disabled while running"
                }

                AddCircleOutlineIcon::class.react {
                    style = jso {
                        marginRight = 0.25.em
                    }
                }

                if (selectedAddCount == 0) {
                    +"Add"
                }
                else {
                    +"Add ($selectedAddCount)"
                }
            }
        }

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
                        if (props.inputBrowserState.browserChecked.isEmpty()) {
                            "No files selected"
                        }
                        else {
                            "No existing files selected"
                        }
                }
                else if (props.inputBrowserState.browserInfoLoading) {
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