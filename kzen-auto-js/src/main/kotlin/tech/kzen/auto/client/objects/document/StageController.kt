package tech.kzen.auto.client.objects.document

//import tech.kzen.auto.common.service.GraphStructureManager
import kotlinx.css.em
import kotlinx.css.fontSize
import kotlinx.css.marginLeft
import kotlinx.css.paddingTop
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.service.store.LocalGraphStore


class StageController(
        props: Props
):
        RPureComponent<StageController.Props, StageController.State>(props),
        LocalGraphStore.Observer,
        NavigationGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var documentControllers: List<DocumentController>
    ): RProps


    class State(
            var structure: GraphStructure?,
            var documentPath: DocumentPath?
    ): RState


    @Suppress("unused")
    class Wrapper(
            private val documentControllers: List<DocumentController>
    ): ReactWrapper<Props> {
        override fun child(input: RBuilder, handler: RHandler<Props>): ReactElement {
            return input.child(StageController::class) {
                attrs {
                    documentControllers = this@Wrapper.documentControllers
                }

                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        async {
            ClientContext.mirroredGraphStore.observe(this)
            ClientContext.navigationRepository.observe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.mirroredGraphStore.unobserve(this)
        ClientContext.navigationRepository.unobserve(this)
    }


    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        setState {
            structure = graphDefinition.successful.graphStructure
        }
    }


    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {}


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {
        setState {
            structure = graphDefinition.successful.graphStructure
        }
    }


    override fun handleNavigation(
            documentPath: DocumentPath?,
            parameters: RequestParams
    ) {
        setState {
            this.documentPath = documentPath
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun documentArchetypeName(): ObjectName? {
        val notation = state.structure?.graphNotation
                ?: return null

        val path = state.documentPath
                ?: return null

        return DocumentArchetype.archetypeName(notation, path)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val archetypeName = documentArchetypeName()

        if (archetypeName == null) {
            renderMissingDocument()
        }
        else {
            renderDocumentController(archetypeName)
        }
    }


    private fun RBuilder.renderMissingDocument() {
        styledDiv {
            css {
                marginLeft = 2.em
                paddingTop = 2.em
            }

            val mainDocuments = state
                    .structure
                    ?.graphNotation
                    ?.let { AutoConventions.mainDocuments(it) }
                    ?: listOf()

            styledDiv {
                css {
                    fontSize = 1.5.em
                }

                if (mainDocuments.isEmpty()) {
                    +"Please create a document from the sidebar (left)"
                }
                else {
                    +"Please select a document from the sidebar (left)"
                }
            }
        }
    }


    private fun RBuilder.renderDocumentController(
            archetypeName: ObjectName
    ) {
//        console.log("%%%%%% renderDocumentController", props.documentControllers)
        val documentController = props.documentControllers
                .single { archetypeName == it.archetypeLocation().objectPath.name }

        documentController.child(this) {}
    }
}