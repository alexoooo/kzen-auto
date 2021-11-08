package tech.kzen.auto.client.objects.document.plugin

import kotlinx.css.*
import react.*
import react.dom.div
import styled.css
import styled.styledDiv
import styled.styledH2
import styled.styledSpan
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.common.AttributePathValueEditor
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.plugin.PluginConventions
import tech.kzen.auto.common.objects.document.plugin.model.ReportDefinerDetail
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect


@Suppress("unused")
class PluginController(
    props: react.Props
):
    RPureComponent<react.Props, PluginController.State>(props),
    SessionGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        val separatorWidth = 2.px
//        val separatorColor = Color("#c3c3c3")

        fun tryMainLocation(clientState: SessionState): ObjectLocation? {
            val documentPath = clientState
                .navigationRoute
                .documentPath
                ?: return null

            val documentNotation = clientState
                .graphStructure()
                .graphNotation
                .documents[documentPath]
                ?: return null

            if (! PluginConventions.isPlugin(documentNotation)) {
                return null
            }

            return documentPath.toMainObjectLocation()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface State: react.State {
        var clientState: SessionState?
        var detailList: List<ReportDefinerDetail>?
        var listingError: String?
//        var mainObjectLocation: ObjectLocation?
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation
    ):
        DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }

        override fun child(input: RBuilder, handler: RHandler<react.Props>)/*: ReactElement*/ {
            input.child(PluginController::class) {
                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: react.Props) {
        clientState = null
        detailList = null
        listingError = null
//        mainObjectLocation = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.sessionGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.sessionGlobal.unobserve(this)
    }


    override fun componentDidUpdate(prevProps: react.Props, prevState: State, snapshot: Any) {
        val clientState = state.clientState
            ?: return

        if (clientState.navigationRoute.documentPath != prevState.clientState?.navigationRoute?.documentPath) {
            loadInfo()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: SessionState) {
//        console.log("#!#@!#@! onClientState - ${clientState.imperativeModel}")
        setState {
            this.clientState = clientState
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun loadInfo() {
        val clientState = state.clientState
            ?: return

        val mainObjectLocation = tryMainLocation(clientState)
            ?: return

        async {
            @Suppress("MoveVariableDeclarationIntoWhen")
            val result = ClientContext.restClient.performDetached(mainObjectLocation)

            when (result) {
                is ExecutionSuccess -> {
                    @Suppress("UNCHECKED_CAST")
                    val infoCollectionList = result.value.get() as List<Map<String, Any?>>
                    val infoList = infoCollectionList.map { ReportDefinerDetail.ofCollection(it) }
                    setState {
                        this.detailList = infoList
                        listingError = null
                    }
                }

                is ExecutionFailure -> {
                    setState {
                        detailList = null
                        listingError = result.errorMessage
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val clientState = state.clientState
            ?: return

        val mainObjectLocation = tryMainLocation(clientState)
            ?: return

        styledDiv {
            css {
                margin(5.em, 2.em, 2.em, 2.em)
            }

            renderPathEditor(mainObjectLocation, clientState)
            renderInfoListing()
        }
    }


    private fun RBuilder.renderPathEditor(mainObjectLocation: ObjectLocation, clientState: SessionState) {
        child(AttributePathValueEditor::class) {
            attrs {
                labelOverride = "Plugin Jar File Path"

                this.clientState = clientState
                objectLocation = mainObjectLocation
                attributePath = PluginConventions.jarPathAttributeName.asAttributePath()

                valueType = TypeMetadata.long

                onChange = {
                    loadInfo()
                }
            }
        }
    }


    private fun RBuilder.renderInfoListing() {
        val infoList = state.detailList
        val listingError = state.listingError

        styledDiv {
            css {
                marginTop = 0.5.em
            }

            when {
                infoList != null ->
                    renderInfoList(infoList)

                listingError != null ->
                    +"Error: $listingError"

                else ->
                    +"Loading..."
            }
        }
    }


    private fun RBuilder.renderInfoList(detailList: List<ReportDefinerDetail>) {
        if (detailList.isEmpty()) {
            +"Empty"
            return
        }

        for (processorDefinitionDetail in detailList) {
            styledDiv {
                key = processorDefinitionDetail.coordinate.asString()

                css {
                    filter = "drop-shadow(0 1px 1px gray)"
                    borderRadius = 3.px
                    backgroundColor = Color.white
                    width = 100.pct.minus(2.em)
                    padding(1.em)
                    marginTop = 1.em
                }

                styledH2 {
                    css {
                        marginTop = (-0.5).em
                        marginBottom = 0.px
                    }
                    +processorDefinitionDetail.coordinate.name
                }

                div {
                    +"File extensions: "
                    styledSpan {
                        css {
                            fontFamily = "monospace"
                        }
                        +processorDefinitionDetail.extensions.joinToString()
                    }
                }

                div {
                    val dataEncoding = processorDefinitionDetail.dataEncoding.textEncoding?.charsetName ?: "Binary"
                    +"Data encoding: "
                    styledSpan {
                        css {
                            fontFamily = "monospace"
                        }
                        +dataEncoding
                    }
                }

                div {
                    +"Model type: "

                    styledSpan {
                        css {
                            fontFamily = "monospace"
                        }
                        +processorDefinitionDetail.modelType.asString()
                    }
                }

                if (processorDefinitionDetail.priority != -1) {
                    div {
                        +"Priority: "
                        styledSpan {
                            css {
                                fontFamily = "monospace"
                            }
                            +processorDefinitionDetail.priority.toString()
                        }
                    }
                }
            }
        }
    }
}