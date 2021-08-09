package tech.kzen.auto.client.objects.document.pipeline.input.browse

import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputState
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputStore
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.common.objects.document.report.listing.InputBrowserInfo
import tech.kzen.auto.common.objects.document.report.spec.input.InputBrowserSpec
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.common.model.locate.ObjectLocation


class InputBrowserController(
    props: Props
):
    RPureComponent<InputBrowserController.Props, InputBrowserController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var mainLocation: ObjectLocation
        var spec: InputBrowserSpec
        var selectedDataLocation: Set<DataLocation>
        var open: Boolean
        var forceOpen: Boolean
        var inputState: PipelineInputState
        var inputStore: PipelineInputStore
    }


    interface State: RState {
        var requestPending: Boolean
//        var inputBrowserInfo: InputBrowserInfo?
//        var infoError: String?
//        var infoLoading: Boolean
//        var dirChangeError: String?
//        var selected: PersistentSet<DataLocation>
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        requestPending = false
//        inputBrowserInfo = null
//        infoLoading = false
//        selected = persistentSetOf()
    }


    override fun componentDidUpdate(
        prevProps: Props,
        prevState: State,
        snapshot: Any
    ) {
        if (props.open && props.inputState.browser.browserInfo == null && ! state.requestPending) {
            setState {
                requestPending = true
            }
        }

        if (state.requestPending && ! prevState.requestPending) {
            props.inputStore.browser.browserLoadInfoAsync()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        if (! props.open) {
            // NB: keep state when browser is hidden
            return
        }

        if (! props.forceOpen) {
            styledDiv {
                css {
                    borderTopWidth = ReportController.separatorWidth
                    borderTopColor = ReportController.separatorColor
                    borderTopStyle = BorderStyle.solid
                    width = 100.pct
                    fontSize = 1.5.em
                }

                +"Browser"
            }
        }

        val inputBrowserInfo = props.inputState.browser.browserInfo
        val infoError = props.inputState.browser.browserInfoError

        when {
            infoError != null ->
                renderInfoError(infoError)

            inputBrowserInfo == null ->
                renderInfoLoadingInitial()

            else ->
                renderInfoLoaded(inputBrowserInfo)
        }
    }


    private fun RBuilder.renderInfoError(error: String) {
        renderError(error)
        renderPath(null)
    }


    private fun RBuilder.renderError(error: String) {
        styledDiv {
            css {
                color = Color.red
            }

            +"Error: $error"
        }
    }


    private fun RBuilder.renderInfoLoadingInitial() {
        styledDiv {
            css {
                fontFamily = "monospace"
            }

            +props.spec.directory.asString()
        }
    }


    private fun RBuilder.renderInfoLoaded(inputBrowserInfo: InputBrowserInfo) {
//        val browserError = props.inputState.browserChangeError()
//        if (browserError != null) {
//            renderError(browserError)
//        }

        renderControls(inputBrowserInfo)

        styledDiv {
            css {
                marginTop = 0.5.em
                marginBottom = 0.5.em
            }

            renderPath(inputBrowserInfo)
        }

        child(InputBrowserTableController::class) {
            attrs {
                mainLocation = props.mainLocation
                hasFilter = props.spec.filter.isNotBlank()
                dataLocationInfos = inputBrowserInfo.files
                selectedDataLocation = props.selectedDataLocation
                loading = props.inputState.browser.browserInfoLoading
                inputState = props.inputState
                inputStore = props.inputStore
            }
        }
    }


    private fun RBuilder.renderControls(inputBrowserInfo: InputBrowserInfo) {
        styledDiv {
            child(InputBrowserActionController::class) {
                attrs {
                    mainLocation = props.mainLocation
//                    hasFilter = props.spec.filter.isNotBlank()
                    dataLocationInfos = inputBrowserInfo.files
                    selectedDataLocation = props.selectedDataLocation
                    disabled = props.inputState.browser.browserInfoLoading
                    inputState = props.inputState
                    inputStore = props.inputStore
                }
            }

            styledSpan {
                css {
                    float = Float.right
                }
                child(InputBrowserFilterController::class) {
                    attrs {
                        spec = props.spec
                        inputStore = props.inputStore
                    }
                }
            }
        }
    }


    private fun RBuilder.renderPath(inputBrowserInfoOrNull: InputBrowserInfo?) {
        val errorMode = inputBrowserInfoOrNull == null

        val browserDir =
            props.inputState.browser.browserDirChangeRequest ?:
            inputBrowserInfoOrNull?.browseDir ?:
            props.spec.directory

        child(InputBrowserPathController::class) {
            attrs {
                mainLocation = props.mainLocation
                this.browseDir = browserDir
                this.errorMode = errorMode
                inputStore = props.inputStore
            }
        }
    }
}