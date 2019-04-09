package tech.kzen.auto.client.objects.sidebar

import kotlinx.css.em
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.NavigationManager
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.edit.NotationEvent


class SidebarController(
        props: Props
):
        RComponent<SidebarController.Props, SidebarController.State>(props),
        ModelManager.Observer,
        NavigationManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var documentArchetypes: List<DocumentArchetype>
    ): RProps


    class State(
            var structure: GraphStructure?,
            var documentPath: DocumentPath?
    ): RState


    @Suppress("unused")
    class Wrapper(
            private val documentArchetypes: List<DocumentArchetype>
    ): ReactWrapper<SidebarController.Props> {
        override fun child(input: RBuilder, handler: RHandler<SidebarController.Props>): ReactElement {
            return input.child(SidebarController::class) {
                attrs {
                    documentArchetypes = this@Wrapper.documentArchetypes
                }

                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
//        println("ProjectController - Subscribed")
        async {
            ClientContext.modelManager.observe(this)
            ClientContext.navigationManager.observe(this)
        }
    }


    override fun componentWillUnmount() {
//        println("ProjectController - Un-subscribed")
        ClientContext.modelManager.unobserve(this)
        ClientContext.navigationManager.unobserve(this)
    }


    override fun componentDidUpdate(
            prevProps: Props,
            prevState: State,
            snapshot: Any
    ) {
        val structure = state.structure
                ?: return

        val mainDocuments = AutoConventions.mainDocuments(structure.graphNotation)

        if (state.documentPath == null && ! mainDocuments.isEmpty()) {
            ClientContext.navigationManager.goto(mainDocuments[0])
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(projectStructure: GraphStructure, event: NotationEvent?) {
//        console.log("^ handleModel")
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
        val structure = state.structure
                ?: return

        styledDiv {
            css {
                paddingTop = 1.em
                paddingRight = 1.em
                paddingBottom = 1.em
                paddingLeft = 1.em
            }

            child(SidebarFolder::class) {
                attrs {
                    this.structure = structure
                    selectedDocumentPath = state.documentPath
                    documentArchetypes = props.documentArchetypes
                }
            }
        }
    }
}