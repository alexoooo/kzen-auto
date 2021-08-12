package tech.kzen.auto.client.objects.document.pipeline.input

import kotlinx.css.*
import react.*
import react.dom.attrs
import react.dom.div
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.pipeline.input.browse.InputBrowserController
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputState
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputStore
import tech.kzen.auto.client.objects.document.pipeline.input.select.InputSelectedController
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.edge.ReportBottomEgress
import tech.kzen.auto.client.wrap.material.*
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.listing.InputSelectedInfo
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.lib.common.model.locate.ObjectLocation


class PipelineInputController(
    props: Props
):
    RPureComponent<PipelineInputController.Props, PipelineInputController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val hoverRow = Color("rgb(220, 220, 220)")
        val selectedRow = Color("rgb(220, 220, 255)")
        val selectedHoverRow = Color("rgb(190, 190, 240)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var mainLocation: ObjectLocation
        var spec: InputSpec
        var inputState: PipelineInputState
        var inputStore: PipelineInputStore
    }


    interface State: RState {
        var browserOpen: Boolean
        var inputSelection: InputSelectedInfo?
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        browserOpen = false
        inputSelection = null
    }


    override fun componentDidUpdate(
        prevProps: Props,
        prevState: State,
        snapshot: Any
    ) {
//        val clientState = state.clientState
//                ?: return
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun isBrowserForceOpen(): Boolean {
        return props.spec.selection.locations.isEmpty()
    }


    private fun isBrowserOpen(): Boolean {
        return isBrowserForceOpen() || state.browserOpen
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onToggleBrowser() {
        if (isBrowserForceOpen()) {
            return
        }

        setState {
            browserOpen = ! browserOpen
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                position = Position.relative
                filter = "drop-shadow(0 1px 1px gray)"
                height = 100.pct
            }

            styledDiv {
                css {
                    borderRadius = 3.px
                    backgroundColor = Color.white
                    width = 100.pct
                }

                styledDiv {
                    css {
                        padding(1.em)
                    }

                    renderContent()
                }
            }

            child(ReportBottomEgress::class) {
                attrs {
                    this.egressColor = Color.white
                    parentWidth = 100.pct
                }
            }
        }
    }


    private fun RBuilder.renderContent() {
        renderHeader()

//        val editDisabled =
//            props.reportState.isInitiating() ||
//                    props.reportState.isTaskRunning()

        div {
            attrs {
//                if (editDisabled) {
//                    if (props.reportState.isInitiating()) {
//                        title = "Disabled while loading"
//                    }
//                }
            }

            renderBrowseFiles()
            renderSelectedFiles()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderHeader() {
        styledDiv {
            css {
                marginBottom = 0.25.em
            }

            styledSpan {
                css {
                    height = 2.em
                    width = 2.5.em
                    position = Position.relative
                }

                child(InputIcon::class) {
                    attrs {
                        style = reactStyle {
                            position = Position.absolute
                            fontSize = 2.5.em
                            top = (-16.5).px
                            left = (-6.5).px
                        }
                    }
                }
            }

            styledSpan {
                css {
                    marginLeft = 1.25.em
                    fontSize = 2.em
                }

                +"Input"
            }

            styledSpan {
                css {
                    float = Float.right
                }

                if (! isBrowserForceOpen()) {
                    styledSpan {
                        css {
                            width = 2.em
                            height = 2.em
                            marginRight = 0.25.em
                        }
                        styledSpan {
                            css {
                                float = Float.left
                            }
                            renderBrowserToggle()
                        }
                    }
                }

                child(MaterialFade::class) {
                    attrs {
                        `in` = props.inputState.anyLoading()
                        timeout = FadeTimeout(appear = 500, enter = 5000)
                    }

                    child(MaterialCircularProgress::class) {
                        attrs {
                            style = reactStyle {
                                width = 2.em
                                height = 2.em
                            }
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderBrowserToggle() {
        child(MaterialButton::class) {
            attrs {
                variant = "outlined"
                size = "small"

                onClick = {
                    onToggleBrowser()
                }

                style = reactStyle {
                    if (isBrowserOpen()) {
                        backgroundColor = ReportController.selectedColor
                    }
                    borderWidth = 2.px
                    marginTop = 0.px
                }

                title = when {
                    isBrowserOpen() ->
                        "Hide browser"

                    else ->
                        "Show browser"
                }
            }

            child(FolderOpenIcon::class) {
                attrs {
                    style = reactStyle {
                        marginRight = 0.25.em
                    }
                }
            }

            +"Browser"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderBrowseFiles(/*editDisabled: Boolean*/) {
        child(InputBrowserController::class) {
            attrs {
                mainLocation = props.mainLocation
                spec = props.spec.browser
                selectedDataLocation = props.spec.selection.dataLocationSet()
                open = isBrowserOpen()
                forceOpen = isBrowserForceOpen()
                inputBrowserState = props.inputState.browser
                inputStore = props.inputStore
            }
        }
    }


    private fun RBuilder.renderSelectedFiles(/*editDisabled: Boolean*/) {
        child(InputSelectedController::class) {
            attrs {
                mainLocation = props.mainLocation
                spec = props.spec.selection
                browserOpen = isBrowserOpen()
                inputSelectedState = props.inputState.selected
                inputStore = props.inputStore
            }
        }
    }
}