package tech.kzen.auto.client.objects.document.report.input.select

import csstype.*
import emotion.react.css
import js.core.jso
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Size
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.react
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.objects.document.report.input.select.model.InputSelectedState
import tech.kzen.auto.client.objects.document.report.run.model.ReportRunProgress
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.GroupWorkIcon
import tech.kzen.auto.client.wrap.material.MoreHorizIcon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.report.spec.input.InputSelectionSpec
import tech.kzen.lib.common.model.locate.ObjectLocation


//---------------------------------------------------------------------------------------------------------------------
external interface InputSelectedControllerProps: Props {
    var mainLocation: ObjectLocation
    var spec: InputSelectionSpec
    var browserOpen: Boolean
    var runningOrLoading: Boolean
    var inputSelectedState: InputSelectedState
    var progress: ReportRunProgress?
    var inputStore: ReportInputStore
}


external interface InputSelectedControllerState: State {
    var showDetails: Boolean
    var showGroupBy: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class InputSelectedController(
    props: InputSelectedControllerProps
):
    RPureComponent<InputSelectedControllerProps, InputSelectedControllerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun InputSelectedControllerState.init(props: InputSelectedControllerProps) {
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
    override fun ChildrenBuilder.render() {
        if (props.spec.locations.isEmpty()) {
            return
        }

        if (props.browserOpen) {
            div {
                css {
                    borderTopWidth = ReportController.separatorWidth
                    borderTopColor = ReportController.separatorColor
                    borderTopStyle = LineStyle.solid
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

        InputSelectedTableController::class.react {
            showDetails = state.showDetails
            spec = props.spec
            inputSelectedState = props.inputSelectedState
            progress = props.progress
            inputStore = props.inputStore
        }
    }


    private fun ChildrenBuilder.renderErrors() {
        val error = props.inputSelectedState.selectionError()
            ?: return

        div {
            css {
                color = NamedColor.red
            }

            +"Error: $error"
        }
    }


    private fun ChildrenBuilder.renderActions() {
        div {
            css {
                width = 100.pct
            }

            div {
                css {
                    marginRight = 1.em
                    display = Display.inlineBlock
                    minWidth = 8.5.em
                }
                InputSelectedRemoveController::class.react {
                    mainLocation = props.mainLocation
                    disabled = props.runningOrLoading
                    inputSelectedState = props.inputSelectedState
                    inputStore = props.inputStore
                }
            }

            div {
                css {
                    marginRight = 1.em
                    display = Display.inlineBlock
                }
                InputSelectedTypeController::class.react {
                    spec = props.spec
                    editDisabled = props.runningOrLoading
                    inputSelectedState = props.inputSelectedState
                    inputStore = props.inputStore
                }
            }

            div {
                css {
                    display = Display.inlineBlock
                }
                InputSelectedFormatController::class.react {
                    spec = props.spec
                    editDisabled = props.runningOrLoading
                    inputSelectedState = props.inputSelectedState
                    inputStore = props.inputStore
                }
            }

            div {
                css {
                    float = Float.right
                    display = Display.inlineBlock
                    marginTop = 18.px
                }
//                renderGroupByToggle()
//                renderDetailToggle()
            }
        }
    }


    private fun ChildrenBuilder.renderGroupByToggle() {
        Button {
            variant = ButtonVariant.outlined
            size = Size.small

            onClick = {
                onToggleGroupBy()
            }

            css {
                if (state.showGroupBy) {
                    backgroundColor = ReportController.selectedColor
                }
                borderWidth = 2.px
                color = NamedColor.black
                borderColor = Color("#c4c4c4")
            }

            title =
                if (state.showGroupBy) {
                    "Hide: Group By"
                }
                else {
                    "Show: Group By"
                }

            GroupWorkIcon::class.react {
                style = jso {
                    marginRight = 0.25.em
                }
            }

            +"Group"
        }
    }


    private fun ChildrenBuilder.renderDetailToggle() {
        Button {
            variant = ButtonVariant.outlined
            size = mui.material.Size.small

            onClick = {
                onToggleFolders()
            }

//                color = "inherit"
            css {
                marginLeft = 1.em

                if (state.showDetails) {
                    backgroundColor = ReportController.selectedColor
                }
                borderWidth = 2.px
                color = NamedColor.black
                borderColor = Color("#c4c4c4")
            }

            title =
                if (state.showDetails) {
                    "Hide: Details"
                }
                else {
                    "Show: Details"
                }

            MoreHorizIcon::class.react {
                style = jso {
                    marginLeft = (-0.25).em
                    marginRight = (-0.25).em
                }
            }
        }
    }


    private fun ChildrenBuilder.renderGroupBy() {
        if (! state.showGroupBy) {
            return
        }

        InputSelectedGroupController::class.react {
            spec = props.spec
            editDisabled = props.runningOrLoading
            inputStore = props.inputStore
        }
    }
}