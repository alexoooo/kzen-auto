package tech.kzen.auto.client.objects

import kotlinx.browser.window
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
import tech.kzen.auto.client.objects.ribbon.HeaderController
import tech.kzen.auto.client.objects.sidebar.SidebarController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.store.LocalGraphStore


//---------------------------------------------------------------------------------------------------------------------
external interface ProjectControllerProps: Props {
    var sidebarController: SidebarController.Wrapper
    var headerController: HeaderController.Wrapper
    var stageController: StageController.Wrapper
}


external interface ProjectControllerState: State {
    var structure: GraphStructure?
    var commandErrorMessage: String?
    var commandErrorRequest: NotationCommand?

    var headerHeight: Int?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class ProjectController(
    props: ProjectControllerProps
):
    RPureComponent<ProjectControllerProps, ProjectControllerState>(props),
    LocalGraphStore.Observer,
    NavigationGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val shadowWidth = 6
        private val sidebarWidth = 16.em
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var headerElement: RefObject<HTMLElement> = createRef()


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val sidebarController: SidebarController.Wrapper,
        private val headerController: HeaderController.Wrapper,
        private val stageController: StageController.Wrapper
    ): ReactWrapper<Props> {
        override fun child(input: RBuilder, handler: RHandler<Props>) {
            input.child(ProjectController::class) {
                attrs {
                    sidebarController = this@Wrapper.sidebarController
                    headerController = this@Wrapper.headerController
                    stageController = this@Wrapper.stageController
                }

                handler()
            }
        }
    }


    // TODO: is there a way to directly observe the headerElement height change?
    private val handleResize: (Event?) -> Unit = { _ ->
        val height = headerElement.current?.clientHeight ?: 0
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
            ClientContext.navigationGlobal.observe(this)
        }

        window.addEventListener("resize", handleResize)
    }


    override fun componentWillUnmount() {
//        println("ProjectController - Un-subscribed")
        ClientContext.mirroredGraphStore.unobserve(this)
        ClientContext.navigationGlobal.unobserve(this)

        window.addEventListener("resize", handleResize)
    }


    override fun componentDidUpdate(
            prevProps: ProjectControllerProps,
            prevState: ProjectControllerState,
            snapshot: Any
    ) {
        val height = headerElement.current?.clientHeight ?: 0

        if (height != prevState.headerHeight) {
            setState {
                headerHeight = height
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        setState {
            structure = graphDefinition.graphStructure
            commandErrorMessage = null
        }
    }


    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {
//        console.log("^^^ onCommandFailure", command.toString(), cause)
        setState {
            commandErrorRequest = command
            commandErrorMessage = "${cause.message}"
        }
    }


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {
        setState {
            structure = graphDefinition.graphStructure
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleNavigation(
            documentPath: DocumentPath?,
            parameters: RequestParams
    ) {
        async {
            // NB: account for possible header resize
            delay(1)
            handleResize.invoke(null)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val graphNotation = state.structure?.graphNotation
        if (graphNotation == null) {
            +"Loading..."
        }
        else {
            renderBody(graphNotation)
        }
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
                    ref = headerElement

                    props.headerController.child(this) {}
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

        val headerHeight = (state.headerHeight ?: 64).px
        styledDiv {
            css {
                marginTop = headerHeight
                marginLeft = sidebarWidth
//                position = Position.relative
            }

            if (state.commandErrorMessage != null) {
                styledDiv {
                    css {
                        color = Color.red
                    }
                    +"Command Error: ${state.commandErrorMessage} - ${state.commandErrorRequest}"
                }
            }

            val context = StageController.CoordinateContext(
                stageTop = headerHeight,
                stageLeft = sidebarWidth)

            StageController.StageContext.Provider(context) {
                props.stageController.child(this) {}
            }
        }
    }
}
