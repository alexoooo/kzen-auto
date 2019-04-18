package tech.kzen.auto.client.objects.document

import kotlinx.css.em
import react.*
import styled.css
import styled.styledH3
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.NavigationManager
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.edit.NotationEvent


class StageController(
        props: StageController.Props
):
        RComponent<StageController.Props, StageController.State>(props),
        ModelManager.Observer,
        NavigationManager.Observer
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
    ): ReactWrapper<StageController.Props> {
        override fun child(input: RBuilder, handler: RHandler<StageController.Props>): ReactElement {
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
            ClientContext.modelManager.observe(this)
            ClientContext.navigationManager.observe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.modelManager.unobserve(this)
        ClientContext.navigationManager.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(graphStructure: GraphStructure, event: NotationEvent?) {
        setState {
            structure = graphStructure
        }
    }


    override fun handleNavigation(documentPath: DocumentPath?) {
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
        styledH3 {
            css {
                marginLeft = 1.em
                paddingTop = 1.em
            }

            val mainDocuments = state
                    .structure
                    ?.graphNotation
                    ?.let { AutoConventions.mainDocuments(it) }
                    ?: listOf()

            if (mainDocuments.isEmpty()) {
                +"Please create a file from the sidebar (left)"
            }
            else {
                +"Please select a file from the sidebar (left)"
            }
        }
    }


    private fun RBuilder.renderDocumentController(
            archetypeName: ObjectName
    ) {
        val documentController = props.documentControllers
                .single { archetypeName == it.type().name() }

        documentController.child(this) {}
    }
}