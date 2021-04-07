package tech.kzen.auto.client.objects.document

import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.store.LocalGraphStore


class StageController(
        props: Props
):
        RPureComponent<StageController.Props, StageController.State>(props),
        LocalGraphStore.Observer,
        NavigationGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    data class CoordinateContext(
        val stageTop: LinearDimension,
        val stageLeft: LinearDimension
    ) {
        companion object {
            val origin = CoordinateContext(0.px, 0.px)
        }
    }


    companion object {
        val StageContext = createContext(CoordinateContext.origin)
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var documentControllers: List<DocumentController>
    }


    interface State: RState {
        var structure: GraphStructure?
        var documentPath: DocumentPath?
        var transition: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
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
    override fun State.init(props: Props) {
        structure = null
        documentPath = null
        transition = false
    }


    override fun componentDidUpdate(
        prevProps: Props,
        prevState: State,
        snapshot: Any
    ) {
        if (state.documentPath != prevState.documentPath &&
                state.documentPath != null &&
                prevState.documentPath != null
        ) {
            setState {
                transition = true
            }
        }

        if (state.transition) {
            setState {
                transition = false
            }
        }
    }


    override fun componentDidMount() {
        async {
            ClientContext.mirroredGraphStore.observe(this)
            ClientContext.navigationGlobal.observe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.mirroredGraphStore.unobserve(this)
        ClientContext.navigationGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
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
        if (state.transition) {
//            console.log("^^^ TRANSITION")
            return
        }

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
                .singleOrNull { archetypeName == it.archetypeLocation().objectPath.name }

        if (documentController == null) {
            +"Document: $archetypeName"
        }
        else {
            documentController.child(this) {}
        }
    }
}