package tech.kzen.auto.client.objects

import kotlinx.coroutines.delay
import kotlinx.css.*
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import react.*
import react.dom.div
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.StageController
import tech.kzen.auto.client.objects.ribbon.RibbonController
import tech.kzen.auto.client.objects.sidebar.SidebarController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.NavigationManager
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.service.store.LocalGraphStore
import kotlin.browser.window


@Suppress("unused")
class ProjectController(
        props: Props
):
        RPureComponent<ProjectController.Props, ProjectController.State>(props),
        LocalGraphStore.Observer,
        NavigationManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val shadowWidth = 6
        private val sidebarWidth = 16.em
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var headerElement: HTMLElement? = null


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var sidebarController: SidebarController.Wrapper,
            var ribbonController: RibbonController.Wrapper,
            var stageController: StageController.Wrapper
    ): RProps


    class State(
            var structure: GraphStructure?,
            var commandError: String?,

            var headerHeight: Int?
    ): RState


    @Suppress("unused")
    class Wrapper(
            private val sidebarController: SidebarController.Wrapper,
            private val ribbonController: RibbonController.Wrapper,
            private val stageController: StageController.Wrapper
    ): ReactWrapper<Props> {
        override fun child(input: RBuilder, handler: RHandler<Props>): ReactElement {
            return input.child(ProjectController::class) {
                attrs {
                    ribbonController = this@Wrapper.ribbonController
                    sidebarController = this@Wrapper.sidebarController
                    stageController = this@Wrapper.stageController
                }

                handler()
            }
        }
    }


    // TODO: is there a way to directly observe the headerElement height change?
    private val handleResize: (Event?) -> Unit = { _ ->
        val height = headerElement?.clientHeight ?: 0
        if (state.headerHeight != height) {
            setState {
                headerHeight = height
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        async {
            ClientContext.mirroredGraphStore.observe(this)
            ClientContext.navigationManager.observe(this)
        }

        window.addEventListener("resize", handleResize)
    }


    override fun componentWillUnmount() {
//        println("ProjectController - Un-subscribed")
        ClientContext.mirroredGraphStore.unobserve(this)
        ClientContext.navigationManager.unobserve(this)

        window.addEventListener("resize", handleResize)
    }


    override fun componentDidUpdate(
            prevProps: Props,
            prevState: State,
            snapshot: Any
    ) {
        val height = headerElement?.clientHeight ?: 0

        if (height != prevState.headerHeight) {
            setState {
                headerHeight = height
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        setState {
            structure = graphDefinition.successful.graphStructure
            commandError = null
        }
    }


    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {
        setState {
            commandError = "${cause.message}"
        }
    }


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {
        setState {
            structure = graphDefinition.successful.graphStructure
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleNavigation(documentPath: DocumentPath?) {
        async {
            // NB: account for possible header resize
            delay(1)
            handleResize.invoke(null)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
//        styledDiv {
            val graphNotation = state.structure?.graphNotation
            if (graphNotation == null) {
                +"Loading..."
            }
            else {
                renderBody(graphNotation)
            }
//        }
    }


    private fun RBuilder.renderBody(
            graphNotation: GraphNotation
    ) {
        styledDiv {
            css {
                position = Position.fixed
                width = 0.px
                height = 100.vh
                left = 0.px
                top = 0.px
                zIndex = 999
                filter = "drop-shadow(0 1px ${shadowWidth}px gray)"
                display = Display.flex
                flexDirection = FlexDirection.column
            }

            styledDiv {
                css {
                    width = 100.vw
                }

                div {
                    ref {
                        headerElement = it as? HTMLElement
                    }

                    props.ribbonController.child(this) {
                        attrs {
                            notation = graphNotation
                        }
                    }
                }
            }

            styledDiv {
                css {
                    backgroundColor = Color.white
                    width = sidebarWidth

                    borderTopWidth = 1.px
                    borderTopStyle = BorderStyle.solid
                    borderTopColor = Color.lightGray

                    flexGrow = 1.0
                    overflow = Overflow.auto
                }

                props.sidebarController.child(this) {}
            }
        }

        styledDiv {
            css {
                marginLeft = sidebarWidth
                marginTop = (state.headerHeight ?: 64).px
            }

            if (state.commandError != null) {
                +"!! ERROR: ${state.commandError}"
            }

            props.stageController.child(this) {}
        }
    }
}
