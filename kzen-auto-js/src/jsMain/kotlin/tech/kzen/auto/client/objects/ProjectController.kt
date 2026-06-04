package tech.kzen.auto.client.objects

import emotion.react.css
import kotlinx.browser.window
import kotlinx.coroutines.delay
import org.w3c.dom.events.Event
import react.ChildrenBuilder
import react.Props
import react.RefObject
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.StageController
import tech.kzen.auto.client.objects.ribbon.HeaderController
import tech.kzen.auto.client.objects.ribbon.HeaderModel
import tech.kzen.auto.client.objects.sidebar.SidebarController
import tech.kzen.auto.client.objects.sidebar.SidebarModel
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*
import web.html.HTMLElement
import kotlin.time.Duration.Companion.milliseconds


//---------------------------------------------------------------------------------------------------------------------
external interface ProjectControllerProps: Props {
    var sidebarController: SidebarController.Wrapper
    var headerController: HeaderController.Wrapper
    var stageController: StageController.Wrapper
    var archetypeLocations: List<ObjectLocation>
    var mirroredGraphStore: MirroredGraphStore
    var navigationGlobal: NavigationGlobal
}


external interface ProjectControllerState: State {
    var structure: GraphStructure?
    var headerModel: HeaderModel?
    var sidebarModel: SidebarModel?
    var documentPath: DocumentPath?
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

    private val headerModelBuilder = HeaderModel.Builder()
    private val sidebarModelBuilder = SidebarModel.Builder(props.archetypeLocations)


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val sidebarController: SidebarController.Wrapper,
        private val headerController: HeaderController.Wrapper,
        private val stageController: StageController.Wrapper,
        private val archetypeLocations: List<ObjectLocation>,
        @Service private val mirroredGraphStore: MirroredGraphStore,
        @Service private val navigationGlobal: NavigationGlobal
    ): ReactWrapper<Props> {
        override fun ChildrenBuilder.child(block: Props.() -> Unit) {
            ProjectController::class.react {
                sidebarController = this@Wrapper.sidebarController
                headerController = this@Wrapper.headerController
                stageController = this@Wrapper.stageController
                archetypeLocations = this@Wrapper.archetypeLocations
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                navigationGlobal = this@Wrapper.navigationGlobal
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
            props.mirroredGraphStore.observe(this)
            props.navigationGlobal.observe(this)
        }

        window.addEventListener("resize", handleResize)
    }


    override fun componentWillUnmount() {
        props.mirroredGraphStore.unobserve(this)
        props.navigationGlobal.unobserve(this)

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
        val nextHeaderModel = headerModelBuilder.update(graphDefinition.graphStructure)
        val nextSidebarModel = sidebarModelBuilder.update(graphDefinition.graphStructure)
        setState {
            structure = graphDefinition.graphStructure
            headerModel = nextHeaderModel
            sidebarModel = nextSidebarModel
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


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {
//        console.log("^^^ onStoreRefresh: " + graphDefinition.graphStructure)
        val nextSidebarModel = sidebarModelBuilder.update(graphDefinitionAttempt.graphStructure)
        val nextHeaderModel = headerModelBuilder.update(graphDefinitionAttempt.graphStructure)
        setState {
            structure = graphDefinitionAttempt.graphStructure
            headerModel = nextHeaderModel
            sidebarModel = nextSidebarModel
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleNavigation(
        documentPath: DocumentPath?,
        parameters: RequestParams
    ) {
        setState {
            this.documentPath = documentPath
        }

        async {
            // NB: account for possible header resize
            delay(1.milliseconds)
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
                    props.headerController.child(this) {
                        headerModel = state.headerModel
                    }
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

                props.sidebarController.child(this) {
                    sidebarModel = state.sidebarModel
                    documentPath = state.documentPath
                }
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
