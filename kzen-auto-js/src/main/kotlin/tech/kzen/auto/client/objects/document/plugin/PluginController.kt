package tech.kzen.auto.client.objects.document.plugin

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.span
import react.react
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.common.AttributePathValueEditor
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.plugin.PluginConventions
import tech.kzen.auto.common.objects.document.plugin.model.ReportDefinerDetail
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface PluginControllerState: State {
    var clientState: SessionState?
    var detailList: List<ReportDefinerDetail>?
    var listingError: String?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class PluginController(
    props: Props
):
    RPureComponent<Props, PluginControllerState>(props),
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
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation
    ):
        DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }


        override fun header(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {}
            }
        }


        override fun body(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    PluginController::class.react {
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun PluginControllerState.init(props: Props) {
        clientState = null
        detailList = null
        listingError = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.sessionGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.sessionGlobal.unobserve(this)
    }


    override fun componentDidUpdate(prevProps: Props, prevState: PluginControllerState, snapshot: Any) {
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
    override fun ChildrenBuilder.render() {
        val clientState = state.clientState
            ?: return

        val mainObjectLocation = tryMainLocation(clientState)
            ?: return

        div {
            css {
                margin = Margin(5.em, 2.em, 2.em, 2.em)
            }

            renderPathEditor(mainObjectLocation, clientState)
            renderInfoListing()
        }
    }


    private fun ChildrenBuilder.renderPathEditor(mainObjectLocation: ObjectLocation, clientState: SessionState) {
        AttributePathValueEditor::class.react {
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


    private fun ChildrenBuilder.renderInfoListing() {
        val infoList = state.detailList
        val listingError = state.listingError

        div {
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


    private fun ChildrenBuilder.renderInfoList(detailList: List<ReportDefinerDetail>) {
        if (detailList.isEmpty()) {
            +"Empty"
            return
        }

        for (processorDefinitionDetail in detailList) {
            div {
                key = processorDefinitionDetail.coordinate.asString()

                css {
                    filter = dropShadow(0.px, 1.px, 1.px, NamedColor.gray)
                    borderRadius = 3.px
                    backgroundColor = NamedColor.white
                    width = 100.pct.minus(2.em)
                    padding = Padding(1.em, 1.em, 1.em, 1.em)
                    marginTop = 1.em
                }

                h2 {
                    css {
                        marginTop = (-0.5).em
                        marginBottom = 0.px
                    }
                    +processorDefinitionDetail.coordinate.name
                }

                div {
                    +"File extensions: "
                    span {
                        css {
                            fontFamily = FontFamily.monospace
                        }
                        +processorDefinitionDetail.extensions.joinToString()
                    }
                }

                div {
                    val dataEncoding = processorDefinitionDetail.dataEncoding.textEncoding?.charsetName ?: "Binary"
                    +"Data encoding: "
                    span {
                        css {
                            fontFamily = FontFamily.monospace
                        }
                        +dataEncoding
                    }
                }

                div {
                    +"Model type: "

                    span {
                        css {
                            fontFamily = FontFamily.monospace
                        }
                        +processorDefinitionDetail.modelType.asString()
                    }
                }

                if (processorDefinitionDetail.priority != -1) {
                    div {
                        +"Priority: "
                        span {
                            css {
                                fontFamily = FontFamily.monospace
                            }
                            +processorDefinitionDetail.priority.toString()
                        }
                    }
                }
            }
        }
    }
}