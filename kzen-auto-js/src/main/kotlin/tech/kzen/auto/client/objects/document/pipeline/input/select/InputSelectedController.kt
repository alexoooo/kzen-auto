package tech.kzen.auto.client.objects.document.pipeline.input.select

import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.pipeline.PipelineController
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputStore
import tech.kzen.auto.client.objects.document.pipeline.input.select.model.InputSelectedState
import tech.kzen.auto.client.objects.document.pipeline.run.model.PipelineRunProgress
import tech.kzen.auto.client.wrap.material.GroupWorkIcon
import tech.kzen.auto.client.wrap.material.MaterialButton
import tech.kzen.auto.client.wrap.material.MoreHorizIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.spec.input.InputSelectionSpec
import tech.kzen.lib.common.model.locate.ObjectLocation


class InputSelectedController(
    props: Props
):
    RPureComponent<InputSelectedController.Props, InputSelectedController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var mainLocation: ObjectLocation
        var spec: InputSelectionSpec
        var browserOpen: Boolean
        var runningOrLoading: Boolean
        var inputSelectedState: InputSelectedState
        var progress: PipelineRunProgress?
        var inputStore: PipelineInputStore
    }


    interface State: react.State {
        var showDetails: Boolean
        var showGroupBy: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        showDetails = false
        showGroupBy = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onToggleFolders() {
        setState {
            showDetails = ! showDetails
        }
    }


    private fun onToggleGroupBy() {
        setState {
            showGroupBy = ! showGroupBy
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        if (props.spec.locations.isEmpty()) {
            return
        }

        if (props.browserOpen) {
            styledDiv {
                css {
                    borderTopWidth = PipelineController.separatorWidth
                    borderTopColor = PipelineController.separatorColor
                    borderTopStyle = BorderStyle.solid
                    marginTop = 1.em
                    width = 100.pct
                    fontSize = 1.5.em
                }

                +"Selected"
            }
        }

        renderErrors()
        renderActions()
        renderGroupBy()

        child(InputSelectedTableController::class) {
            attrs {
                showDetails = state.showDetails
                spec = props.spec
                inputSelectedState = props.inputSelectedState
                progress = props.progress
                inputStore = props.inputStore
            }
        }
    }


    private fun RBuilder.renderErrors() {
        val error = props.inputSelectedState.selectionError()
            ?: return

        styledDiv {
            css {
                color = Color.red
            }

            +"Error: $error"
        }
    }


    private fun RBuilder.renderActions() {
        styledDiv {
            css {
                width = 100.pct
            }

            styledDiv {
                css {
                    marginRight = 1.em
                    display = Display.inlineBlock
                    minWidth = 8.5.em
                }
                child(InputSelectedRemoveController::class) {
                    attrs {
                        mainLocation = props.mainLocation
                        disabled = props.runningOrLoading
                        inputSelectedState = props.inputSelectedState
                        inputStore = props.inputStore
                    }
                }
            }

            styledDiv {
                css {
                    marginRight = 1.em
                    display = Display.inlineBlock
                }
                child(InputSelectedTypeController::class) {
                    attrs {
                        spec = props.spec
                        editDisabled = props.runningOrLoading
                        inputSelectedState = props.inputSelectedState
                        inputStore = props.inputStore
                    }
                }
            }

            styledDiv {
                css {
                    display = Display.inlineBlock
                }
                child(InputSelectedFormatController::class) {
                    attrs {
                        spec = props.spec
                        editDisabled = props.runningOrLoading
                        inputSelectedState = props.inputSelectedState
                        inputStore = props.inputStore
                    }
                }
            }

            styledDiv {
                css {
                    float = Float.right
                    display = Display.inlineBlock
                    marginTop = 18.px
                }
                renderGroupByToggle()
                renderDetailToggle()
            }
        }
    }


    private fun RBuilder.renderGroupByToggle() {
        child(MaterialButton::class) {
            attrs {
                variant = "outlined"
                size = "small"

                onClick = {
                    onToggleGroupBy()
                }

                style = reactStyle {
                    if (state.showGroupBy) {
                        backgroundColor = PipelineController.selectedColor
                    }
                    borderWidth = 2.px
                }

                title =
                    if (state.showGroupBy) {
                        "Hide: Group By"
                    }
                    else {
                        "Show: Group By"
                    }
            }

            child(GroupWorkIcon::class) {
                attrs {
                    style = reactStyle {
                        marginRight = 0.25.em
                    }
                }
            }

            +"Group"
        }
    }


    private fun RBuilder.renderDetailToggle() {
        child(MaterialButton::class) {
            attrs {
                variant = "outlined"
                size = "small"

                onClick = {
                    onToggleFolders()
                }

                style = reactStyle {
                    marginLeft = 1.em

                    if (state.showDetails) {
                        backgroundColor = PipelineController.selectedColor
                    }
                    borderWidth = 2.px
                }

                title =
                    if (state.showDetails) {
                        "Hide: Details"
                    }
                    else {
                        "Show: Details"
                    }
            }

            child(MoreHorizIcon::class) {
                attrs {
                    style = reactStyle {
                        marginLeft = (-0.25).em
                        marginRight = (-0.25).em
                    }
                }
            }
        }
    }


    private fun RBuilder.renderGroupBy() {
        if (! state.showGroupBy) {
            return
        }

        child(InputSelectedGroupController::class) {
            attrs {
                spec = props.spec
                editDisabled = props.runningOrLoading
                inputStore = props.inputStore
            }
        }
    }
}