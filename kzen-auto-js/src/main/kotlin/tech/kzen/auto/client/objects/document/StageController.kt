package tech.kzen.auto.client.objects.document

import react.*
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.NavigationManager
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.api.model.DocumentPath
import tech.kzen.lib.common.api.model.ObjectName
import tech.kzen.lib.common.api.model.ObjectPath
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
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

        val document = notation.documents.values[path]
                ?: return

        val mainObject = document.objects.values[ObjectPath.parse("main")]
                ?: return

        val parent = mainObject
                .attributes[NotationConventions.isName]
                ?.asString()
                ?.let { ObjectName(it) }
                ?: return

        for (documentController in props.documentControllers) {
//            console.log("^^^^^^ documentController", documentController)
            if (parent != documentController.type().name()) {
                continue
            }

            documentController.child(this) {}
        }
    }
}