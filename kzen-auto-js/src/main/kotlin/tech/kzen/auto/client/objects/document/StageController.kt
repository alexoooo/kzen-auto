package tech.kzen.auto.client.objects.document

import react.*
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.NavigationManager
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.model.document.DocumentPath
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
    override suspend fun handleModel(projectStructure: GraphStructure, event: NotationEvent?) {
        setState {
            structure = projectStructure
        }
    }


    override fun handleNavigation(documentPath: DocumentPath?) {
        setState {
            this.documentPath = documentPath
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val notation = state.structure?.graphNotation
                ?: return

        val path = state.documentPath
                ?: return

        val parent = DocumentArchetype.archetypeName(notation, path)
                ?: return

        val documentController = props.documentControllers
                .single { parent == it.type().name() }

        documentController.child(this) {}
    }
}