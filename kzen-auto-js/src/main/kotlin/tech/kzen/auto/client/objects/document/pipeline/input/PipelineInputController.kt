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
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.edge.ReportBottomEgress
import tech.kzen.auto.client.wrap.material.FolderOpenIcon
import tech.kzen.auto.client.wrap.material.InputIcon
import tech.kzen.auto.client.wrap.material.MaterialButton
import tech.kzen.auto.client.wrap.material.MaterialCircularProgress
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.listing.InputSelectionInfo
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.lib.common.model.locate.ObjectLocation


class PipelineInputController(
    props: Props
):
    RPureComponent<PipelineInputController.Props, PipelineInputController.State>(props)//,
//    SessionGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var mainLocation: ObjectLocation
        var spec: InputSpec
        var inputState: PipelineInputState
        var inputStore: PipelineInputStore
    }


    interface State: RState {
        var browserOpen: Boolean
        var inputSelection: InputSelectionInfo?
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        browserOpen = false
        inputSelection = null
    }


//    override fun componentDidMount() {
//        ClientContext.sessionGlobal.observe(this)
//    }
//
//
//    override fun componentWillUnmount() {
//        ClientContext.sessionGlobal.unobserve(this)
//    }


    override fun componentDidUpdate(
        prevProps: Props,
        prevState: State,
        snapshot: Any
    ) {
//        val clientState = state.clientState
//                ?: return
    }


    //-----------------------------------------------------------------------------------------------------------------
//    override fun onClientState(clientState: SessionState) {
//        clientState.
//    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun isBrowserForceOpen(): Boolean {
        return props.spec.selection.locations.isEmpty()
//        val listingSelected = state.inputSelection
//        return listingSelected != null && listingSelected.isEmpty()
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
                    renderBrowserToggle()
                }

                // TODO: phase in, see:
                //  https://stackoverflow.com/questions/47929977/material-ui-linear-progress-animation-when-using-data
                if (props.inputState.anyLoading()) {
                    child(MaterialCircularProgress::class) {
                        attrs {
//                            val rootClass = json()
//                            rootClass["root"] = "fade-in"
//                            classes = rootClass

                            style = reactStyle {
//                                +"fade-in"
                                width = 2.em
                                height = 2.em

//                                transition("opacity", duration = 5.0.s)
//
//                                opacity = if (props.inputState.anyLoading()) { 1 } else { 0 }
//
//                                display =
//                                    if (props.inputState.anyLoading()) {
//                                        Display.inline
//                                    }
//                                    else {
//                                        Display.none
//                                    }
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

            +"Browse"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderBrowseFiles(/*editDisabled: Boolean*/) {
        child(InputBrowserController::class) {
            attrs {
                mainLocation = props.mainLocation
                spec = props.spec.browser
                open = isBrowserOpen()
                forceOpen = isBrowserForceOpen()
                inputState = props.inputState
                inputStore = props.inputStore
            }
        }
    }


    private fun RBuilder.renderSelectedFiles(/*editDisabled: Boolean*/) {
//        child(InputSelected::class) {
//            attrs {
//                reportState = props.reportState
//                dispatcher = props.dispatcher
//                this.editDisabled = editDisabled
//                browserOpen = isBrowserOpen()
//            }
//        }
    }
}