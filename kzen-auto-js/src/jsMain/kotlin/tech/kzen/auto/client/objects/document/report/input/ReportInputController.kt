package tech.kzen.auto.client.objects.document.report.input

import emotion.react.css
import js.core.jso
import mui.material.*
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.react
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.input.browse.InputBrowserController
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputState
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.objects.document.report.input.select.InputSelectedController
import tech.kzen.auto.client.objects.document.report.run.model.ReportRunProgress
import tech.kzen.auto.client.objects.document.report.widget.ReportBottomEgress
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.FadeTimeout
import tech.kzen.auto.client.wrap.material.FolderOpenIcon
import tech.kzen.auto.client.wrap.material.InputIcon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.report.listing.InputSelectedInfo
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ReportInputControllerProps: Props {
    var mainLocation: ObjectLocation
    var spec: InputSpec
    var runningOrLoading: Boolean
    var inputState: ReportInputState
    var inputStore: ReportInputStore
    var progress: ReportRunProgress?
}


external interface ReportInputControllerState: State {
    var browserOpen: Boolean
    var inputSelection: InputSelectedInfo?
}


//---------------------------------------------------------------------------------------------------------------------
class ReportInputController(
    props: ReportInputControllerProps
):
    RPureComponent<ReportInputControllerProps, ReportInputControllerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val hoverRow = Color("rgb(220, 220, 220)")
        val selectedRow = Color("rgb(220, 220, 255)")
        val selectedHoverRow = Color("rgb(190, 190, 240)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ReportInputControllerState.init(props: ReportInputControllerProps) {
        browserOpen = false
        inputSelection = null
    }


    override fun componentDidUpdate(
        prevProps: ReportInputControllerProps,
        prevState: ReportInputControllerState,
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

        // TODO: updating state from state, is that a problem?
        val toggled = ! state.browserOpen
//        console.log("$@#$#@ onToggleBrowser - $toggled")

        setState {
            this.browserOpen = toggled
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                position = Position.relative
                filter = dropShadow(0.px, 1.px, 1.px, NamedColor.gray)
                height = 100.pct
            }

            div {
                css {
                    borderRadius = 3.px
                    backgroundColor = NamedColor.white
                    width = 100.pct
                }

                div {
                    css {
                        padding = Padding(1.em, 1.em, 1.em, 1.em)
                    }

                    renderContent()
                }
            }

            ReportBottomEgress::class.react {
                this.egressColor = NamedColor.white
                parentWidth = 100.pct
            }
        }
    }


    private fun ChildrenBuilder.renderContent() {
        renderHeader()

        div {
            if (props.runningOrLoading) {
                title = "Disabled while running"
            }

            renderBrowseFiles()
            renderSelectedFiles()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderHeader() {
        div {
            css {
                marginBottom = 0.25.em
            }

            span {
                css {
                    height = 2.em
                    width = 2.5.em
                    position = Position.relative
                }

                InputIcon::class.react {
                    style = jso {
                        position = Position.absolute
                        fontSize = 2.5.em
                        top = (-16.5).px
                        left = (-6.5).px
                    }
                }
            }

            span {
                css {
                    marginLeft = 1.25.em
                    fontSize = 2.em
                }

                +"Input"
            }

            span {
                css {
                    float = Float.right
                }

                if (! isBrowserForceOpen()) {
                    span {
                        css {
                            width = 2.em
                            height = 2.em
                            marginRight = 0.25.em
                        }
                        span {
                            css {
                                float = Float.left
                            }
                            renderBrowserToggle()
                        }
                    }
                }

                Fade {
                    `in` = props.inputState.anyLoading()
                    timeout = FadeTimeout(appear = 500, enter = 5000)

                    CircularProgress {
                        css {
                            width = 2.em
                            height = 2.em
                        }
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderBrowserToggle() {
        Button {
            variant = ButtonVariant.outlined
            size = Size.small

            sx {
                if (isBrowserOpen()) {
                    backgroundColor = ReportController.selectedColor
                }
                color = NamedColor.black
                borderColor = Color("#777777")
            }

            onClick = {
                onToggleBrowser()
            }

            title = when {
                isBrowserOpen() ->
                    "Hide browser"

                else ->
                    "Show browser"
            }

            FolderOpenIcon::class.react {
                style = jso {
                    marginRight = 0.25.em
                }
            }

            +"Browser"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderBrowseFiles() {
        InputBrowserController::class.react {
            mainLocation = props.mainLocation
            spec = props.spec.browser
            selectedDataLocation = props.spec.selection.dataLocationSet()
            open = isBrowserOpen()
            forceOpen = isBrowserForceOpen()
            inputBrowserState = props.inputState.browser
            inputStore = props.inputStore
        }
    }


    private fun ChildrenBuilder.renderSelectedFiles() {
        InputSelectedController::class.react {
            mainLocation = props.mainLocation
            spec = props.spec.selection
            browserOpen = isBrowserOpen()
            runningOrLoading = props.runningOrLoading
            inputSelectedState = props.inputState.selected
            progress = props.progress
            inputStore = props.inputStore
        }
    }
}