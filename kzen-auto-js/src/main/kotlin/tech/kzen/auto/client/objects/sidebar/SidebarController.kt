package tech.kzen.auto.client.objects.sidebar

import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.store.LocalGraphStore


class SidebarController(
        props: Props
):
        RPureComponent<SidebarController.Props, SidebarController.State>(props),
        LocalGraphStore.Observer,
        NavigationGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var archetypeLocations: List<ObjectLocation>
    ): RProps


    class State(
            var structure: GraphStructure?,
            var documentPath: DocumentPath?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
            private val archetypes: List<ObjectLocation>
    ): ReactWrapper<Props> {
        override fun child(input: RBuilder, handler: RHandler<Props>): ReactElement {
            return input.child(SidebarController::class) {
                attrs {
                    archetypeLocations = this@Wrapper.archetypes
                }

                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
//        println("ProjectController - Subscribed")
        async {
            ClientContext.mirroredGraphStore.observe(this)
            ClientContext.navigationGlobal.observe(this)
        }
    }


    override fun componentWillUnmount() {
//        println("ProjectController - Un-subscribed")
        ClientContext.mirroredGraphStore.unobserve(this)
        ClientContext.navigationGlobal.unobserve(this)
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
            ClientContext.navigationGlobal.goto(mainDocuments[0])
        }
    }


    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
//        console.log("^ handleModel")
        setState {
            structure = graphDefinition.graphStructure
        }
    }


    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {}


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {
        setState {
            structure = graphDefinition.graphStructure
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
    override fun RBuilder.render() {
        val structure = state.structure
                ?: return

        styledDiv {
            css {
                paddingTop = 1.em
                paddingRight = 1.em
                paddingBottom = 0.5.em
                paddingLeft = 1.em
            }

            child(SidebarFolder::class) {
                attrs {
                    this.graphStructure = structure
                    selectedDocumentPath = state.documentPath
                    archetypeLocations = props.archetypeLocations
                }
            }
        }
    }
}