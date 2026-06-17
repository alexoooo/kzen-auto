package tech.kzen.auto.client.objects

import emotion.react.css
import kotlinx.browser.window
import kotlinx.coroutines.delay
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent
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
import tech.kzen.auto.client.service.storage.SidebarPreferences
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
import web.resize.ResizeObserver
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
    var sidebarWidthPx: Double
    var sidebarCollapsed: Boolean
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

        // sidebar layout widths, in px (resizable + persisted; see SidebarPreferences)
        @Suppress("ConstPropertyName")
        private const val defaultSidebarWidth = 256.0
        @Suppress("ConstPropertyName")
        private const val minSidebarWidth = 140.0
        @Suppress("ConstPropertyName")
        private const val maxSidebarWidthCap = 640.0
        @Suppress("ConstPropertyName")
        private const val collapsedSidebarWidth = 52.0

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
    private var headerResizeObserver: ResizeObserver? = null
    private var observedHeaderElement: HTMLElement? = null

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


    // dragging the right-edge handle resizes the sidebar; listeners live on window so the drag keeps tracking
    // even when the cursor leaves the thin handle
    private val onResizeMove: (Event?) -> Unit = onResizeMove@ { event ->
        val mouseEvent = event as? MouseEvent
            ?: return@onResizeMove
        val upper = maxOf(minSidebarWidth, maxSidebarWidth())
        val clamped = mouseEvent.clientX.toDouble().coerceIn(minSidebarWidth, upper)
        if (clamped != state.sidebarWidthPx) {
            setState {
                sidebarWidthPx = clamped
            }
        }
    }

    private val onResizeEnd: (Event?) -> Unit = { _ ->
        window.removeEventListener("mousemove", onResizeMove)
        window.removeEventListener("mouseup", onResizeEnd)
        SidebarPreferences.saveWidth(state.sidebarWidthPx)
    }

    private fun onResizeStart() {
        window.addEventListener("mousemove", onResizeMove)
        window.addEventListener("mouseup", onResizeEnd)
    }

    private fun maxSidebarWidth(): Double {
        return minOf(window.innerWidth.toDouble() * 0.6, maxSidebarWidthCap)
    }

    private fun toggleCollapsed() {
        val next = !state.sidebarCollapsed
        setState {
            sidebarCollapsed = next
        }
        SidebarPreferences.saveCollapsed(next)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ProjectControllerState.init(props: ProjectControllerProps) {
        sidebarWidthPx = SidebarPreferences.loadWidth(defaultSidebarWidth)
        sidebarCollapsed = SidebarPreferences.loadCollapsed(false)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        async {
            props.mirroredGraphStore.observe(this)
            props.navigationGlobal.observe(this)
        }

        window.addEventListener("resize", handleResize)
        attachHeaderResizeObserver()
    }


    override fun componentWillUnmount() {
        props.mirroredGraphStore.unobserve(this)
        props.navigationGlobal.unobserve(this)

        // NB: was addEventListener (copy-paste slip) — that re-added the handler on unmount and leaked it.
        window.removeEventListener("resize", handleResize)
        headerResizeObserver?.disconnect()
        headerResizeObserver = null
        observedHeaderElement = null
    }


    override fun componentDidUpdate(
        prevProps: ProjectControllerProps,
        prevState: ProjectControllerState,
        snapshot: Any
    ) {
        // NB: the header element first attaches once graphNotation loads (render shows "Loading..." until
        //     then), and React can swap it on later renders — (re)bind the observer whenever it changes.
        attachHeaderResizeObserver()

        val height = headerElement.current?.clientHeight ?: 0

        if (height != prevState.headerHeight) {
            setState {
                headerHeight = height
            }
        }
    }


    // Observe the header's own height directly. The header grows/shrinks independently of this component —
    // switching ribbon tabs or entering/leaving Raw view changes which (and how many rows of) insertion-tool
    // buttons the ribbon shows. Those changes re-render the ribbon, not ProjectController, so componentDidUpdate
    // alone leaves headerHeight (the body's marginTop) stale and the body overlaps/gaps under the fixed header.
    private fun attachHeaderResizeObserver() {
        val element = headerElement.current
        if (element === observedHeaderElement) {
            return
        }

        headerResizeObserver?.disconnect()
        observedHeaderElement = element

        if (element == null) {
            headerResizeObserver = null
            return
        }

        val observer = ResizeObserver { _, _ -> handleResize(null) }
        observer.observe(element)
        headerResizeObserver = observer
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
        val collapsed = state.sidebarCollapsed
        val effectiveWidth = (if (collapsed) collapsedSidebarWidth else state.sidebarWidthPx).px
        val headerHeight = (state.headerHeight ?: 64).px

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
                    width = effectiveWidth

                    borderTopWidth = 1.px
                    borderTopStyle = LineStyle.solid
                    borderTopColor = NamedColor.lightgray

                    flexGrow = number(1.0)
                    overflow = Auto.auto

                    // reveal the "Project" row's collapse button whenever the mouse is anywhere over the sidebar.
                    // this scroll container fills the full sidebar height (flex-grow), so it covers the empty area
                    // below the rows too — unlike a child whose percentage height can't resolve against a flex
                    // parent. CSS :hover (not a React hover-state field) so there's no re-render on mouse move.
                    "&:hover [data-collapse-button]" {
                        opacity = number(1.0)
                    }
                }

                props.sidebarController.child(this) {
                    sidebarModel = state.sidebarModel
                    documentPath = state.documentPath
                    this.collapsed = collapsed
                    onToggleCollapsed = ::toggleCollapsed
                }
            }

            // drag-to-resize handle pinned to the sidebar's right edge; outside the overflow:auto column so it
            // doesn't scroll with sidebar content. Hidden while collapsed.
            if (!collapsed) {
                div {
                    css {
                        position = Position.absolute
                        top = headerHeight
                        bottom = 0.px
                        left = effectiveWidth
                        width = 6.px
                        marginLeft = (-3).px
                        cursor = Cursor.colResize
                        zIndex = integer(1000)

                        "&:hover" {
                            backgroundColor = NamedColor.lightgray
                        }
                    }

                    onMouseDown = { event ->
                        event.preventDefault()
                        onResizeStart()
                    }
                }
            }
        }

        div {
            css {
                marginTop = headerHeight
                marginLeft = effectiveWidth
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
                stageLeft = effectiveWidth)

            StageController.StageContext.Provider(context) {
                props.stageController.child(this) {}
            }
        }
    }
}
