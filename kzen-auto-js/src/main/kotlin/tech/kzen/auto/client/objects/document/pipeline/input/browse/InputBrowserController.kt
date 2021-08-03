package tech.kzen.auto.client.objects.document.pipeline.input.browse

import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputState
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputStore
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.wrap.material.MaterialCircularProgress
import tech.kzen.auto.common.objects.document.report.listing.InputBrowserInfo
import tech.kzen.auto.common.objects.document.report.spec.input.InputBrowserSpec
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.platform.collect.PersistentSet
import tech.kzen.lib.platform.collect.persistentSetOf
import tech.kzen.lib.platform.collect.toPersistentSet


class InputBrowserController(
    props: Props
):
    RPureComponent<InputBrowserController.Props, InputBrowserController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val hoverRow = Color("rgb(220, 220, 220)")
        private val selectedRow = Color("rgb(220, 220, 255)")
        private val selectedHoverRow = Color("rgb(190, 190, 240)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var mainLocation: ObjectLocation
        var spec: InputBrowserSpec
        var open: Boolean
        var forceOpen: Boolean
        var inputState: PipelineInputState
        var inputStore: PipelineInputStore
    }


    interface State: RState {
//        var requestPending: Boolean
//        var inputBrowserInfo: InputBrowserInfo?
//        var infoError: String?
//        var infoLoading: Boolean
//        var dirChangeError: String?
        var selected: PersistentSet<DataLocation>
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        requestPending = false
//        inputBrowserInfo = null
//        infoLoading = false
        selected = persistentSetOf()
    }


//    override fun componentDidMount() {
//        if (props.open && ! state.requestPending && state.inputBrowserInfo == null) {
//            setState {
//                requestPending = true
//            }
//        }
//    }


    override fun componentDidUpdate(
        prevProps: Props,
        prevState: State,
        snapshot: Any
    ) {
//        println("componentDidUpdate")
        if (! props.open) {
            return
        }

//        if (! state.requestPending && state.inputBrowserInfo == null) {
//            setState {
//                requestPending = true
//            }
//        }
//        else if (state.requestPending && ! prevState.requestPending) {
//            browseRefreshAsync()
//        }

        if (props.inputState.inputBrowserInfo != prevProps.inputState.inputBrowserInfo &&
                ! state.selected.isEmpty()
        ) {
            val available = props.inputState.inputBrowserInfo?.files?.map { it.path }?.toSet() ?: setOf()
            setState {
                selected = selected.filter { it in available }.toPersistentSet()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private fun browseRefreshAsync() {
//        setState {
////            inputBrowserInfo = null
//            infoLoading = true
//            infoError = null
//        }
//
//        async {
//            val result = InputBrowserEndpoint.browse(props.mainLocation)
//
//            setState {
//                infoLoading = false
//                inputBrowserInfo = result.valueOrNull()
//                infoError = result.errorOrNull()
//            }
//        }
//    }


//    private fun onDirChange(dirChangeError: String?) {
//        setState {
//            this.dirChangeError = dirChangeError
//        }
//
//        if (dirChangeError == null) {
//            browseRefreshAsync()
//        }
//    }


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

        val inputBrowserInfo = props.inputState.inputBrowserInfo
        val infoError = props.inputState.infoError

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

//        +"Loading..."
    }


    private fun RBuilder.renderInfoLoaded(inputBrowserInfo: InputBrowserInfo) {
        val dirChangeError = props.inputState.browserDirChangeError
        if (dirChangeError != null) {
            renderError(dirChangeError)
        }

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
                loading = props.inputState.infoLoading
                inputStore = props.inputStore
            }
        }
    }


    private fun RBuilder.renderPath(inputBrowserInfoOrNull: InputBrowserInfo?) {
        val errorMode = inputBrowserInfoOrNull == null

        val browserDir =
            props.inputState.browserDirChangeRequest ?:
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