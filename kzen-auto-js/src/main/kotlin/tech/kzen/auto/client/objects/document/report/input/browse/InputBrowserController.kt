package tech.kzen.auto.client.objects.document.report.input.browse

import csstype.*
import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.react
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.input.browse.model.InputBrowserState
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.report.listing.InputBrowserInfo
import tech.kzen.auto.common.objects.document.report.spec.input.InputBrowserSpec
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.common.model.locate.ObjectLocation


//---------------------------------------------------------------------------------------------------------------------
external interface InputBrowserControllerProps: Props {
    var mainLocation: ObjectLocation
    var spec: InputBrowserSpec
    var selectedDataLocation: Set<DataLocation>
    var open: Boolean
    var forceOpen: Boolean
    var inputBrowserState: InputBrowserState
    var inputStore: ReportInputStore
}


external interface InputBrowserControllerState: State {
    var requestPending: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class InputBrowserController(
    props: InputBrowserControllerProps
):
    RPureComponent<InputBrowserControllerProps, InputBrowserControllerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun InputBrowserControllerState.init(props: InputBrowserControllerProps) {
        requestPending = false
    }


    override fun componentDidUpdate(
        prevProps: InputBrowserControllerProps,
        prevState: InputBrowserControllerState,
        snapshot: Any
    ) {
        if (props.open && props.inputBrowserState.browserInfo == null && ! state.requestPending) {
            setState {
                requestPending = true
            }
        }

        if (state.requestPending && ! prevState.requestPending) {
            props.inputStore.browser.browserLoadInfoAsync()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        if (! props.open) {
            // NB: keep state when browser is hidden
            return
        }

        if (! props.forceOpen) {
            div {
                css {
                    borderTopWidth = ReportController.separatorWidth
                    borderTopColor = ReportController.separatorColor
                    borderTopStyle = LineStyle.solid
                    width = 100.pct
                    fontSize = 1.5.em
                }

                +"Browser"
            }
        }

        val inputBrowserInfo = props.inputBrowserState.browserInfo
        val infoError = props.inputBrowserState.browserInfoError

        when {
            infoError != null ->
                renderInfoError(infoError)

            inputBrowserInfo == null ->
                renderInfoLoadingInitial()

            else ->
                renderInfoLoaded(inputBrowserInfo)
        }
    }


    private fun ChildrenBuilder.renderInfoError(error: String) {
        renderError(error)
        renderPath(null)
    }


    private fun ChildrenBuilder.renderError(error: String) {
        div {
            css {
                color = NamedColor.red
            }

            +"Error: $error"
        }
    }


    private fun ChildrenBuilder.renderInfoLoadingInitial() {
        div {
            css {
                fontFamily = FontFamily.monospace
            }

            +props.spec.directory.asString()
        }
    }


    private fun ChildrenBuilder.renderInfoLoaded(inputBrowserInfo: InputBrowserInfo) {
//        val browserError = props.inputState.browserChangeError()
//        if (browserError != null) {
//            renderError(browserError)
//        }

        renderControls(inputBrowserInfo)

        div {
            css {
                marginTop = 0.5.em
                marginBottom = 0.5.em
            }

            renderPath(inputBrowserInfo)
        }

        InputBrowserTableController::class.react {
            mainLocation = props.mainLocation
            hasFilter = props.spec.filter.isNotBlank()
            dataLocationInfos = inputBrowserInfo.files
            selectedDataLocation = props.selectedDataLocation
            inputBrowserState = props.inputBrowserState
            inputStore = props.inputStore
        }
    }


    private fun ChildrenBuilder.renderControls(inputBrowserInfo: InputBrowserInfo) {
        div {
            InputBrowserActionController::class.react {
                mainLocation = props.mainLocation
                dataLocationInfos = inputBrowserInfo.files
                selectedDataLocation = props.selectedDataLocation
                inputBrowserState = props.inputBrowserState
                inputStore = props.inputStore
            }

            span {
                css {
                    float = Float.right
                }
                InputBrowserFilterController::class.react {
                    spec = props.spec
                    inputStore = props.inputStore
                }
            }
        }
    }


    private fun ChildrenBuilder.renderPath(inputBrowserInfoOrNull: InputBrowserInfo?) {
        val errorMode = inputBrowserInfoOrNull == null

        val browserDir =
            props.inputBrowserState.browserDirChangeRequest ?:
            inputBrowserInfoOrNull?.browseDir ?:
            props.spec.directory

        InputBrowserPathController::class.react {
            mainLocation = props.mainLocation
            this.browseDir = browserDir
            this.errorMode = errorMode
            inputStore = props.inputStore
        }
    }
}