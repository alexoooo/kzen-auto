package tech.kzen.auto.client.objects

import emotion.react.css
import kotlinx.browser.window
import kotlinx.coroutines.delay
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent
import react.ChildrenBuilder
import react.Key
import react.Props
import react.RefObject
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.StageController
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.ribbon.HeaderController
import tech.kzen.auto.client.objects.ribbon.HeaderModel
import tech.kzen.auto.client.objects.sidebar.SidebarController
import tech.kzen.auto.client.objects.sidebar.SidebarModel
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.service.logic.ClientLogicGlobal
import tech.kzen.auto.client.service.logic.LogicRunFrames
import tech.kzen.auto.client.service.storage.SidebarPreferences
import tech.kzen.auto.client.util.DefinitionErrors
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.exec.logic.run.model.LogicRunFrameInfo
import tech.kzen.lib.common.exec.logic.run.model.LogicRunState
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
    var clientStateGlobal: ClientStateGlobal
    var clientLogicGlobal: ClientLogicGlobal
}


external interface ProjectControllerState: State {
    var structure: GraphStructure?
    var headerModel: HeaderModel?
    var sidebarModel: SidebarModel?
    var documentPath: DocumentPath?
    var commandErrorMessage: String?
    var commandErrorRequest: NotationCommand?

    // Objects that failed to define in the current notation (e.g. a meta-declared attribute with no value).
    // Shown as a persistent banner so a corrupted notation is visible up front, not only as an opaque run-time
    // "Missing: <doc>#main". Recomputed on every graph-store update; empty when the notation is clean.
    var definitionErrors: List<DefinitionErrors.Line>

    var headerHeight: Int?
    var sidebarWidthPx: Double
    var sidebarCollapsed: Boolean

    // documentPath → stack depth for documents in the active run's frame tree (root = 0); empty when
    // nothing is running. Threaded to the sidebar so executing documents are highlighted by depth.
    var executingDepths: Map<DocumentPath, Int>

    // documents that hold a retained logic trace (run roots + sub-logic roots); threaded to the sidebar
    // so finished runs still show which documents have an inspectable trace.
    var tracedDocuments: Set<DocumentPath>
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class ProjectController(
    props: ProjectControllerProps
):
    RPureComponent<ProjectControllerProps, ProjectControllerState>(props),
    LocalGraphStore.Observer,
    NavigationGlobal.Observer,
    ClientStateGlobal.Observer
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

    // Per-document hub bridging the header (ribbon) and stage (body) sibling subtrees; provided to both
    // via DocumentBridgeContext in render(). Recreated when the mounted document changes (handleNavigation)
    // so channel/store state never leaks across documents, but stable between renders of the same document
    // so consumers don't needlessly re-render.
    private var documentBridge = DocumentBridge()
    private var bridgeDocumentPath: DocumentPath? = null

    // Auto-follow bookkeeping (plain fields — they must not trigger a render; they mutate on the publish
    // path). `followingRun` = are we currently shadowing the run's deepest executing document?
    // `lastAutoNavigated` = the document WE last goto'd, used to break the goto→hashchange→publish loop.
    private var followingRun: Boolean = false
    private var lastAutoNavigated: DocumentPath? = null

    // Debounces the traced-document query: re-check only when the run status actually changes.
    private var lastTraceFetchKey: String? = null


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val sidebarController: SidebarController.Wrapper,
        private val headerController: HeaderController.Wrapper,
        private val stageController: StageController.Wrapper,
        private val archetypeLocations: List<ObjectLocation>,
        @Service private val mirroredGraphStore: MirroredGraphStore,
        @Service private val navigationGlobal: NavigationGlobal,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val clientLogicGlobal: ClientLogicGlobal
    ): ReactWrapper<Props> {
        override fun ChildrenBuilder.child(block: Props.() -> Unit) {
            ProjectController::class.react {
                sidebarController = this@Wrapper.sidebarController
                headerController = this@Wrapper.headerController
                stageController = this@Wrapper.stageController
                archetypeLocations = this@Wrapper.archetypeLocations
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                navigationGlobal = this@Wrapper.navigationGlobal
                clientStateGlobal = this@Wrapper.clientStateGlobal
                clientLogicGlobal = this@Wrapper.clientLogicGlobal
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
        executingDepths = emptyMap()
        tracedDocuments = emptySet()
        definitionErrors = emptyList()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        async {
            props.mirroredGraphStore.observe(this)
            props.navigationGlobal.observe(this)
            props.clientStateGlobal.observe(this)
        }

        window.addEventListener("resize", handleResize)
        attachHeaderResizeObserver()
    }


    override fun componentWillUnmount() {
        props.mirroredGraphStore.unobserve(this)
        props.navigationGlobal.unobserve(this)
        props.clientStateGlobal.unobserve(this)

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
        val nextDefinitionErrors = DefinitionErrors.all(graphDefinition)
        setState {
            structure = graphDefinition.graphStructure
            headerModel = nextHeaderModel
            sidebarModel = nextSidebarModel
            commandErrorRequest = null
            commandErrorMessage = null
            definitionErrors = nextDefinitionErrors
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
        val nextDefinitionErrors = DefinitionErrors.all(graphDefinitionAttempt)
        setState {
            structure = graphDefinitionAttempt.graphStructure
            headerModel = nextHeaderModel
            sidebarModel = nextSidebarModel
            definitionErrors = nextDefinitionErrors
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleNavigation(
        documentPath: DocumentPath?,
        parameters: RequestParams
    ) {
        // Switching to a different document gets a fresh bridge; a same-document param change keeps it.
        if (documentPath != bridgeDocumentPath) {
            bridgeDocumentPath = documentPath
            documentBridge = DocumentBridge()
        }

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
    override fun onClientState(clientState: ClientState) {
        val frame = clientState.clientLogicState.logicStatus?.active?.frame
        val nextDepths = LogicRunFrames.depthByDocument(frame)

        // Value-equality guard: identical executing-set short-circuits, so the prop reference handed to
        // the sidebar stays stable and unrelated rows (RPureComponent) don't re-render.
        if (nextDepths != state.executingDepths) {
            setState {
                executingDepths = nextDepths
            }
        }

        refreshTracedDocumentsIfNeeded(clientState)
        autoFollow(clientState, frame, nextDepths)
    }


    // Re-query which documents hold a retained trace whenever the run status changes (a fresh
    // LogicStatus.time after a run finishes / a clear), so the sidebar's "has trace" markers stay current
    // once a run is no longer executing. Value-equality guard keeps the prop reference stable otherwise.
    private fun refreshTracedDocumentsIfNeeded(clientState: ClientState) {
        val fetchKey = "${clientState.clientLogicState.logicStatus?.time}"
        if (fetchKey == lastTraceFetchKey) {
            return
        }
        lastTraceFetchKey = fetchKey

        async {
            val traced = props.clientLogicGlobal.tracedDocuments()
            if (traced != state.tracedDocuments) {
                setState {
                    tracedDocuments = traced
                }
            }
        }
    }


    // Make navigation follow the deepest currently-executing document as a stepped / slow-motion run
    // chains across documents. Never during a full-speed Run (the frame tree changes too fast — it would
    // thrash the hash and remount document subtrees).
    private fun autoFollow(
        clientState: ClientState,
        frame: LogicRunFrameInfo?,
        runDepths: Map<DocumentPath, Int>
    ) {
        if (frame == null) {
            followingRun = false
            lastAutoNavigated = null
            return
        }

        // Follow only on a *settled* (non-executing) state, NOT the transient Stepping status — for slow-motion
        // exactly as for manual stepping. A Step Over / Step Out briefly pushes a child frame on the stack
        // mid-step; following Stepping would dive into it and bounce back out. Step Into still follows into
        // the child because it settles Paused there. Slow-motion settles to Paused between ticks (it's
        // awaitStepSettled's exit condition), so Paused covers its follow points too; its intermediate
        // Stepping publishes (emitted so the sidebar highlight tracks the in-flight frame) must NOT drive
        // navigation, or a slow Step Over would descend into the very child it's stepping over. The settled
        // set also includes the halt pauses (ExplicitPaused / ErrorPaused), so navigation follows to the
        // Pause step / failed step the run halted at — hence !isExecuting() rather than == Paused.
        val runState = clientState.clientLogicState.logicStatus?.active?.state
        if (runState == null || runState.isExecuting()) {
            return
        }

        val current = clientState.navigationRoute.documentPath

        // Engage following while viewing a document that's part of the run; disengage if the user manually
        // navigates to an unrelated document (one we didn't send them to).
        if (current != null && current in runDepths) {
            followingRun = true
        }
        else if (current != null && current != lastAutoNavigated) {
            followingRun = false
        }
        if (! followingRun) {
            return
        }

        val target = LogicRunFrames.deepestLeaf(frame).objectLocation.documentPath

        // Idempotence: only navigate when the target differs from BOTH the current path and the one we
        // last auto-navigated to, so the goto→hashchange→clientState cycle can't loop (and so the user can
        // click back to a parent frame without being immediately yanked to the same leaf).
        if (target != current && target != lastAutoNavigated) {
            lastAutoNavigated = target
            props.navigationGlobal.goto(target)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val graphNotation = state.structure?.graphNotation
        if (graphNotation == null) {
            +"Loading..."
        }
        else {
            // One provider wraps both the header and stage subtrees renderBody emits, so each side
            // reaches the same per-document bridge by key.
            DocumentBridgeContext.Provider(documentBridge) {
                renderBody()
            }
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

                    // Marker so viewport-anchored overlays elsewhere in the body (e.g. the script step
                    // screenshot preview) can measure this fixed header's live height and stay clear of it.
                    asDynamic()["data-app-header"] = ""

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
                    executingDepths = state.executingDepths
                    tracedDocuments = state.tracedDocuments
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
            // NB: dedicated scroll pane for the stage. It is pinned to the region right of the fixed sidebar
            //     and below the fixed header, and owns the stage's scroll (both axes) via overflow:auto.
            //     Previously the stage was window-scrolled with no container, so horizontal window scroll slid
            //     the leftmost content — including the insert "+" buttons — UNDER the fixed sidebar (z-index
            //     999), where it silently intercepted clicks (a Selenium "element click intercepted" on the
            //     last insert button). Scrolling within this pane can never move content left of `left`, so the
            //     leftmost content stays clear of the sidebar. ScriptBranchDisplay's step-add/remove scroll-jump
            //     preserve reads/writes THIS element's scrollTop (found via the data-stage-scroll marker),
            //     not window scroll.
            asDynamic()["data-stage-scroll"] = ""

            css {
                position = Position.fixed
                top = headerHeight
                left = effectiveWidth
                right = 0.px
                bottom = 0.px
                overflow = Auto.auto
            }

            div {
                css {
                    if (state.commandErrorMessage == null) {
                        // NB: avoid refreshing StateController on error change
                        display = None.none
                    }

                    color = NamedColor.red
                    // A long single-token message (e.g. a serialized command carrying a long document name)
                    // would otherwise force the stage wide; break anywhere so the banner wraps instead.
                    overflowWrap = OverflowWrap.anywhere
                }
                +"Command error: ${state.commandErrorMessage} - ${state.commandErrorRequest}"
            }

            // Persistent banner for objects that failed to define in the current notation. Clears itself once the
            // notation is fixed (the next store update recomputes an empty list).
            //
            // NB: the outer `div` is ALWAYS emitted (empty + display:none when clean) so the StageController
            //     Provider after it keeps a STABLE child index — mirroring the command-error div above. As a
            //     *conditional* sibling it index-shifted the stage on every appearance/removal, and React —
            //     matching unkeyed siblings by position — REMOUNTED the entire StageController subtree, and with
            //     it the active document controller (ScriptController / JobController / …). That recreates the
            //     controller's `by lazy` store from ScriptState.initial, discarding ALL per-document UI state
            //     (step expansion, scroll, in-progress editor buffers) every time the notation's definition-error
            //     state toggled — e.g. the first time a RunStep's blank `instructions` is selected and the error
            //     clears. Keeping the slot present (empty `div`, display:none ⇒ zero footprint) holds the stage
            //     in place across toggles, exactly as the command-error div above intends ("avoid refreshing
            //     StateController on error change").
            val definitionErrors = state.definitionErrors
            div {
                css {
                    if (definitionErrors.isEmpty()) {
                        display = None.none
                    }
                    else {
                        // extra top margin so the banner clears the fixed header (and its drop shadow)
                        marginTop = 1.em
                        marginRight = 0.5.em
                        marginBottom = 0.5.em
                        marginLeft = 0.5.em
                        padding = 0.5.em
                        color = NamedColor.red
                        borderWidth = 1.px
                        borderStyle = LineStyle.solid
                        borderColor = NamedColor.red
                        borderRadius = 4.px
                    }
                }

                if (definitionErrors.isNotEmpty()) {
                    div {
                        css {
                            fontWeight = FontWeight.bold
                        }
                        +"Notation error — ${definitionErrors.size} object(s) failed to define"
                    }

                    for (line in definitionErrors) {
                        div {
                            key = Key(line.location.asString())
                            css {
                                marginTop = 0.25.em
                                overflowWrap = OverflowWrap.anywhere
                            }
                            +"${line.location.asString()} — ${line.detail}"
                        }
                    }
                }
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
