package tech.kzen.auto.client.objects.document.report.input.select
//
//import kotlinx.css.*
//import react.*
//import styled.css
//import styled.styledDiv
//import tech.kzen.auto.client.objects.document.report.ReportController
//import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
//import tech.kzen.auto.client.objects.document.report.input.select.model.InputSelectedState
//import tech.kzen.auto.client.objects.document.report.run.model.ReportRunProgress
//import tech.kzen.auto.client.wrap.material.GroupWorkIcon
//import tech.kzen.auto.client.wrap.material.MaterialButton
//import tech.kzen.auto.client.wrap.material.MoreHorizIcon
//import tech.kzen.auto.client.wrap.reactStyle
//import tech.kzen.auto.common.objects.document.report.spec.input.InputSelectionSpec
//import tech.kzen.lib.common.model.locate.ObjectLocation
//
//
////---------------------------------------------------------------------------------------------------------------------
//interface InputSelectedControllerProps: Props {
//    var mainLocation: ObjectLocation
//    var spec: InputSelectionSpec
//    var browserOpen: Boolean
//    var runningOrLoading: Boolean
//    var inputSelectedState: InputSelectedState
//    var progress: ReportRunProgress?
//    var inputStore: ReportInputStore
//}
//
//
//interface InputSelectedControllerState: State {
//    var showDetails: Boolean
//    var showGroupBy: Boolean
//}
//
//
////---------------------------------------------------------------------------------------------------------------------
//class InputSelectedController(
//    props: InputSelectedControllerProps
//):
//    RPureComponent<InputSelectedControllerProps, InputSelectedControllerState>(props)
//{
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun InputSelectedControllerState.init(props: InputSelectedControllerProps) {
//        showDetails = false
//        showGroupBy = false
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun onToggleFolders() {
//        setState {
//            showDetails = ! showDetails
//        }
//    }
//
//
//    private fun onToggleGroupBy() {
//        setState {
//            showGroupBy = ! showGroupBy
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun RBuilder.render() {
//        if (props.spec.locations.isEmpty()) {
//            return
//        }
//
//        if (props.browserOpen) {
//            styledDiv {
//                css {
//                    borderTopWidth = ReportController.separatorWidth
//                    borderTopColor = ReportController.separatorColor
//                    borderTopStyle = BorderStyle.solid
//                    marginTop = 1.em
//                    width = 100.pct
//                    fontSize = 1.5.em
//                }
//
//                +"Selected"
//            }
//        }
//
//        renderErrors()
//        renderActions()
//        renderGroupBy()
//
//        child(InputSelectedTableController::class) {
//            attrs {
//                showDetails = state.showDetails
//                spec = props.spec
//                inputSelectedState = props.inputSelectedState
//                progress = props.progress
//                inputStore = props.inputStore
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderErrors() {
//        val error = props.inputSelectedState.selectionError()
//            ?: return
//
//        styledDiv {
//            css {
//                color = Color.red
//            }
//
//            +"Error: $error"
//        }
//    }
//
//
//    private fun RBuilder.renderActions() {
//        styledDiv {
//            css {
//                width = 100.pct
//            }
//
//            styledDiv {
//                css {
//                    marginRight = 1.em
//                    display = Display.inlineBlock
//                    minWidth = 8.5.em
//                }
//                child(InputSelectedRemoveController::class) {
//                    attrs {
//                        mainLocation = props.mainLocation
//                        disabled = props.runningOrLoading
//                        inputSelectedState = props.inputSelectedState
//                        inputStore = props.inputStore
//                    }
//                }
//            }
//
//            styledDiv {
//                css {
//                    marginRight = 1.em
//                    display = Display.inlineBlock
//                }
//                child(InputSelectedTypeController::class) {
//                    attrs {
//                        spec = props.spec
//                        editDisabled = props.runningOrLoading
//                        inputSelectedState = props.inputSelectedState
//                        inputStore = props.inputStore
//                    }
//                }
//            }
//
//            styledDiv {
//                css {
//                    display = Display.inlineBlock
//                }
//                child(InputSelectedFormatController::class) {
//                    attrs {
//                        spec = props.spec
//                        editDisabled = props.runningOrLoading
//                        inputSelectedState = props.inputSelectedState
//                        inputStore = props.inputStore
//                    }
//                }
//            }
//
//            styledDiv {
//                css {
//                    float = Float.right
//                    display = Display.inlineBlock
//                    marginTop = 18.px
//                }
//                renderGroupByToggle()
//                renderDetailToggle()
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderGroupByToggle() {
//        child(MaterialButton::class) {
//            attrs {
//                variant = "outlined"
//                size = "small"
//
//                onClick = {
//                    onToggleGroupBy()
//                }
//
//                style = reactStyle {
//                    if (state.showGroupBy) {
//                        backgroundColor = ReportController.selectedColor
//                    }
//                    borderWidth = 2.px
//                    color = Color.black
//                    borderColor = Color("#c4c4c4")
//                }
//
//                title =
//                    if (state.showGroupBy) {
//                        "Hide: Group By"
//                    }
//                    else {
//                        "Show: Group By"
//                    }
//            }
//
//            child(GroupWorkIcon::class) {
//                attrs {
//                    style = reactStyle {
//                        marginRight = 0.25.em
//                    }
//                }
//            }
//
//            +"Group"
//        }
//    }
//
//
//    private fun RBuilder.renderDetailToggle() {
//        child(MaterialButton::class) {
//            attrs {
//                variant = "outlined"
//                size = "small"
//
//                onClick = {
//                    onToggleFolders()
//                }
//
////                color = "inherit"
//                style = reactStyle {
//                    marginLeft = 1.em
//
//                    if (state.showDetails) {
//                        backgroundColor = ReportController.selectedColor
//                    }
//                    borderWidth = 2.px
//                    color = Color.black
//                    borderColor = Color("#c4c4c4")
//                }
//
//                title =
//                    if (state.showDetails) {
//                        "Hide: Details"
//                    }
//                    else {
//                        "Show: Details"
//                    }
//            }
//
//            child(MoreHorizIcon::class) {
//                attrs {
//                    style = reactStyle {
//                        marginLeft = (-0.25).em
//                        marginRight = (-0.25).em
//                    }
//                }
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderGroupBy() {
//        if (! state.showGroupBy) {
//            return
//        }
//
//        child(InputSelectedGroupController::class) {
//            attrs {
//                spec = props.spec
//                editDisabled = props.runningOrLoading
//                inputStore = props.inputStore
//            }
//        }
//    }
//}