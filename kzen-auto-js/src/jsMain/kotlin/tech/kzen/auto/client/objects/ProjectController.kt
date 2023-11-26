package tech.kzen.auto.client.objects

import emotion.react.css
import kotlinx.browser.window
import kotlinx.coroutines.delay
import org.w3c.dom.events.Event
import react.*
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.StageController
import tech.kzen.auto.client.objects.ribbon.HeaderController
import tech.kzen.auto.client.objects.sidebar.SidebarController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.store.LocalGraphStore
import web.cssom.*
import web.html.HTMLElement


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
        @Suppress("ConstPropertyName")
        private const val shadowWidth = 6

        private val sidebarWidth = 16.em

        @Suppress("ConstPropertyName")
        private const val suppressErrorDisplayKey = "suppress-error-display"

        val suppressErrorDisplay = LocalGraphStore.Attachment(
            mapOf(suppressErrorDisplayKey to true))

        private fun isSuppressErrorDisplay(attachment: LocalGraphStore.Attachment): Boolean {
            return suppressErrorDisplayKey in attachment.header
        }
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
        override fun ChildrenBuilder.child(block: Props.() -> Unit) {
            ProjectController::class.react {
                sidebarController = this@Wrapper.sidebarController
                headerController = this@Wrapper.headerController
                stageController = this@Wrapper.stageController
                block()
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
    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
//        console.log("^^^ onCommandSuccess", event)
        setState {
            structure = graphDefinition.graphStructure
            commandErrorRequest = null
            commandErrorMessage = null
        }
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {
//        console.log("^^^ onCommandFailure", command.toString(), cause)
        if (isSuppressErrorDisplay(attachment)) {
            setState {
                commandErrorRequest = null
                commandErrorMessage = null
            }
        }
        else {
            setState {
                commandErrorRequest = command
                commandErrorMessage = "${cause.message}"
            }
        }
    }


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {
//        console.log("^^^ onStoreRefresh: " + graphDefinition.graphStructure)
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
    override fun ChildrenBuilder.render() {
        val graphNotation = state.structure?.graphNotation
        if (graphNotation == null) {
            +"Loading..."
        }
        else {
            renderBody(/*graphNotation*/)
        }
    }


    private fun ChildrenBuilder.renderBody() {
        div {
            css {
                position = Position.fixed
                width = 0.px
                height = 100.vh
                left = 0.px
                top = 0.px
                zIndex = integer(999)
                filter = dropShadow(0.px, 1.px, shadowWidth.px, NamedColor.gray)
                display = Display.flex
                flexDirection = FlexDirection.column
            }

            div {
                css {
                    width = 100.vw
                }

                div {
                    ref = headerElement
                    props.headerController.child(this) {}
                }
            }

            div {
                css {
                    backgroundColor = NamedColor.white
                    width = sidebarWidth

                    borderTopWidth = 1.px
                    borderTopStyle = LineStyle.solid
                    borderTopColor = NamedColor.lightgray

                    flexGrow = number(1.0)
                    overflow = Auto.auto
                }

                props.sidebarController.child(this) {}
            }
        }

        val headerHeight = (state.headerHeight ?: 64).px
        div {
            css {
                marginTop = headerHeight
                marginLeft = sidebarWidth
            }

            div {
                css {
                    if (state.commandErrorMessage == null) {
                        // NB: avoid refreshing StateController on error change
                        display = None.none
                    }

                    color = NamedColor.red
                }
                +"Command error: ${state.commandErrorMessage} - ${state.commandErrorRequest}"
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
