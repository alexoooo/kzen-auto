package tech.kzen.auto.client.objects.document.filter

//import kotlinx.css.em
//import kotlinx.css.fontFamily
//import kotlinx.css.fontSize
//import kotlinx.css.marginTop
//import kotlinx.html.title
//import react.*
//import react.dom.div
//import styled.css
//import styled.styledDiv
//import styled.styledSpan
//import tech.kzen.auto.client.objects.document.common.DefaultAttributeEditor
//import tech.kzen.auto.client.service.ClientContext
//import tech.kzen.auto.client.service.global.SessionState
//import tech.kzen.auto.client.util.async
//import tech.kzen.auto.client.wrap.MaterialCardContent
//import tech.kzen.auto.client.wrap.MaterialPaper
//import tech.kzen.auto.common.objects.document.filter.FilterConventions
//import tech.kzen.auto.common.objects.document.filter.OutputInfo
//import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
//import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
//import tech.kzen.lib.common.model.locate.ObjectLocation
//
//
//class FilterOutput(
//    props: Props
//):
//    RPureComponent<FilterOutput.Props, FilterOutput.State>(props)
//{
//    //-----------------------------------------------------------------------------------------------------------------
//    class Props(
//        var mainLocation: ObjectLocation,
//        var clientState: SessionState,
//        var filterRunning: Boolean
//    ): RProps
//
//
//    class State(
//        var loading: Boolean,
//        var outputInfo: OutputInfo?,
//        var error: String?
//    ): RState
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun State.init(props: Props) {
//        loading = false
//        outputInfo = null
//        error = null
//    }
//
//
//    override fun componentDidUpdate(
//        prevProps: Props,
//        prevState: State,
//        snapshot: Any
//    ) {
//        if (props.mainLocation != prevProps.mainLocation) {
//            setState {
//                loading = false
//                outputInfo = null
//                error = null
//            }
//            return
//        }
//
//        if (state.loading) {
//            if (! prevState.loading) {
//                getOutputInfo()
//            }
//            return
//        }
//
//        if (state.outputInfo == null ||
//                props.filterRunning != prevProps.filterRunning
//        ) {
//            setState {
//                loading = true
//            }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun getOutputInfo() {
//        async {
//            val result = ClientContext.restClient.performDetached(
//                props.mainLocation,
//                FilterConventions.actionParameter to FilterConventions.actionLookupOutput)
//
//            when (result) {
//                is ExecutionSuccess -> {
//                    @Suppress("UNCHECKED_CAST")
//                    val resultValue = result.value.get() as Map<String, Any?>
//
//                    setState {
//                        outputInfo = OutputInfo.fromCollection(resultValue)
//                    }
//                }
//
//                is ExecutionFailure -> {
//                    setState {
//                        error = result.errorMessage
//                    }
//                }
//            }
//
//            setState {
//                loading = false
//            }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun onAttributeChanged() {
//        setState {
//            loading = true
//            outputInfo = null
//            error = null
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun RBuilder.render() {
//        child(MaterialPaper::class) {
//            child(MaterialCardContent::class) {
//                styledSpan {
//                    css {
//                        fontSize = 2.em
//                    }
//                    +"Output"
//                }
//
//                renderFile()
//
//                renderInfo()
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderFile() {
//        styledDiv {
//            css {
//                marginTop = 0.5.em
//            }
//
//            attrs {
//                if (props.filterRunning) {
//                    title = "Disabled filter running"
//                }
//            }
//
//            child(DefaultAttributeEditor::class) {
//                attrs {
//                    clientState = props.clientState
//                    objectLocation = props.mainLocation
//                    attributeName = FilterConventions.outputAttribute
//                    labelOverride = "File"
//                    disabled = props.filterRunning
//                    onChange = {
//                        onAttributeChanged()
//                    }
//                }
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderInfo() {
//        val error = state.error
//        if (error != null) {
//            +"Listing error: $error"
//            return
//        }
//
//        val outputInfo = state.outputInfo
//
//        styledDiv {
//            css {
//                marginTop = 0.5.em
//            }
//
//            if (outputInfo == null) {
//                +"..."
//            }
//            else {
//                div {
//                    +"Absolute path: "
//
//                    styledSpan {
//                        css {
//                            fontFamily = "monospace"
//                        }
//
//                        +outputInfo.absolutePath
//                    }
//                }
//
//                if (outputInfo.modifiedTime == null) {
//                    +"Does not exist, will create"
//
//                    if (! outputInfo.folderExists) {
//                        +" (along with containing folder)"
//                    }
//                }
//                else {
//                    +"Exists, last modified: ${outputInfo.modifiedTime}"
//                }
//            }
//        }
//    }
//}