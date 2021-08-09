package tech.kzen.auto.client.objects.document.pipeline.input.browse

import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputState
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputStore
import tech.kzen.auto.client.wrap.material.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.material.MaterialButton
import tech.kzen.auto.client.wrap.material.RemoveCircleOutlineIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo
import tech.kzen.lib.common.model.locate.ObjectLocation


class InputBrowserActionController(
    props: Props
):
    RPureComponent<InputBrowserActionController.Props, InputBrowserActionController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var mainLocation: ObjectLocation
//        var hasFilter: Boolean
        var dataLocationInfos: List<DataLocationInfo>
        var selectedDataLocation: Set<DataLocation>
        var disabled: Boolean
        var inputState: PipelineInputState
        var inputStore: PipelineInputStore
    }


    interface State: RState {
//        var textEdit: Boolean
//        var editDir: String
    }


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
        if (props.inputState.browser.browserChecked.isEmpty()) {
            return listOf()
        }

        return props.inputState.browser.browserChecked.filter { it !in props.selectedDataLocation }
    }


    private fun checkedAlreadySelected(): List<DataLocation> {
        if (props.inputState.browser.browserChecked.isEmpty()) {
            return listOf()
        }

        return props.inputState.browser.browserChecked.filter { it in props.selectedDataLocation }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val selectedAddCount = checkedNotSelected().size
        val selectedRemoveCount = checkedAlreadySelected().size

        styledDiv {
            css {
                minWidth = 8.em
                display = Display.inlineBlock
            }

            child(MaterialButton::class) {
                attrs {
                    variant = "outlined"
                    size = "small"

                    style = reactStyle {
                        marginRight = 1.em
                        borderWidth = 2.px
                    }

                    onClick = {
                        onAddToSelection()
                    }

                    if (selectedAddCount == 0) {
                        disabled = true
                        title =
                            if (props.inputState.browser.browserChecked.isEmpty()) {
                                "No files selected"
                            }
                            else {
                                "No new files selected"
                            }
                    }
                    else if (props.disabled) {
                        disabled = true
                        title = "Disabled while running"
                    }
                }

                child(AddCircleOutlineIcon::class) {
                    attrs {
                        style = reactStyle {
                            marginRight = 0.25.em
                        }
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

        styledDiv {
            css {
                display = Display.inlineBlock
            }

            child(MaterialButton::class) {
                attrs {
                    variant = "outlined"
                    size = "small"

                    style = reactStyle {
                        borderWidth = 2.px
                    }

                    onClick = {
                        onRemoveFromSelection()
                    }

                    if (selectedRemoveCount == 0) {
                        disabled = true
                        title =
                            if (props.inputState.browser.browserChecked.isEmpty()) {
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
                }

                child(RemoveCircleOutlineIcon::class) {
                    attrs {
                        style = reactStyle {
                            marginRight = 0.25.em
                        }
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