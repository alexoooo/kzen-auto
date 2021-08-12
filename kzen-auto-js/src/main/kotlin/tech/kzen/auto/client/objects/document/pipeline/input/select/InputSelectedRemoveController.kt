package tech.kzen.auto.client.objects.document.pipeline.input.select

import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputStore
import tech.kzen.auto.client.objects.document.pipeline.input.select.model.InputSelectedState
import tech.kzen.auto.client.wrap.material.MaterialButton
import tech.kzen.auto.client.wrap.material.RemoveCircleOutlineIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.lib.common.model.locate.ObjectLocation


class InputSelectedRemoveController(
    props: Props
):
    RPureComponent<InputSelectedRemoveController.Props, InputSelectedRemoveController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var mainLocation: ObjectLocation
        var disabled: Boolean
        var inputSelectedState: InputSelectedState
        var inputStore: PipelineInputStore
    }


    interface State: RState


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
    override fun RBuilder.render() {
        val selectedRemoveCount = props.inputSelectedState.selectedChecked.size

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