package tech.kzen.auto.client.objects.ribbon

import emotion.react.css
import mui.material.*
import mui.system.sx
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.service.logic.ClientLogicGlobal
import tech.kzen.auto.client.service.logic.ClientLogicState
import tech.kzen.auto.client.service.logic.LogicRunFrames
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.exec.logic.run.model.LogicRunFrameInfo
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.*
import web.html.HTMLElement


//---------------------------------------------------------------------------------------------------------------------
external interface HeaderRunControllerProps: Props {
    var clientStateGlobal: ClientStateGlobal
    var clientLogicGlobal: ClientLogicGlobal
    var navigationGlobal: NavigationGlobal
}


external interface HeaderRunControllerState: State {
    var runnable: Boolean
    var active: Boolean
    var executing: Boolean
    var dropdownOpen: Boolean
    var frame: LogicRunFrameInfo?

    // Whether the current document has a retained logic trace (i.e. the Clear button has something to
    // do). Detected per document via ClientLogicGlobal.traceMostRecentPresent.
    var hasTrace: Boolean

    // Run-start mode: when set, a failed step pauses the run (to fix + continue) instead of ending
    // it. Read at start only; locked while a run is active.
    var pauseOnError: Boolean

    // Whether the client-paced "slow motion" auto-step loop is currently driving the run.
    var slowLooping: Boolean

    // While slowLooping, whether the loop is the Step-Over variant (stays within the current document)
    // rather than the Step variant (descends into nested logic).
    var slowStepOver: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("ConstPropertyName")
class HeaderRunController (
    props: HeaderRunControllerProps
):
    RPureComponent<HeaderRunControllerProps, HeaderRunControllerState>(props),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        const val runningKey = "running"

        private const val actionStep = "step"
        private const val actionStepOver = "step-over"
        private const val actionStepOut = "step-out"
        private const val actionRunOrPause = "run-pause"
        private const val actionStop = "stop"
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var dropdownAnchorRef: RefObject<HTMLElement> = createRef()

    // Held outside React state — fresh instances per tick would otherwise defeat shallow shouldComponentUpdate.
    private var mainObjectLocation: ObjectLocation? = null
    private var latestFrame: LogicRunFrameInfo? = null

    // Debounces the trace-presence query: re-check only when the document or run status actually changes.
    private var lastTraceFetchKey: String? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun HeaderRunControllerState.init(props: HeaderRunControllerProps) {
        runnable = false
        active = false
        executing = false
        dropdownOpen = false
        frame = null
        pauseOnError = false
        hasTrace = false
        slowLooping = false
        slowStepOver = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)

//        async {
//            ClientContext.executionRepository.observe(this)
//
//            val initialActiveScripts =
//                    ClientContext.restClient.runningHosts()
//
//            val nextActive = state.active + initialActiveScripts
//
//            setState {
//                active = nextActive
//            }
//        }
    }


    override fun componentWillUnmount() {
//        ClientContext.executionRepository.unobserve(this)
        props.clientStateGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val documentPath = clientState.navigationRoute.documentPath
            ?: return

        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
            ?: return

        val isLogic = AutoConventions.isLogic(documentNotation)
        val clientLogicState = clientState.clientLogicState
        val nextFrame = clientLogicState.logicStatus?.active?.frame

        val mainLocation = documentPath.toMainObjectLocation()
        mainObjectLocation = mainLocation
        latestFrame = nextFrame

        val dropdownWasOpen = state.dropdownOpen
        setState {
            runnable = isLogic
            active = clientLogicState.isActive()
            executing = clientLogicState.isExecuting()
            slowLooping = clientLogicState.slowLooping
            slowStepOver = clientLogicState.slowStepOver
            if (dropdownWasOpen) {
                frame = nextFrame
            }
        }

        refreshHasTraceIfNeeded(clientLogicState)
    }


    // Re-check whether ANY document holds a retained trace (Clear is global), after a run/clear bumps the
    // status, so the Clear button enables/disables itself accordingly.
    private fun refreshHasTraceIfNeeded(
        clientLogicState: ClientLogicState
    ) {
        val fetchKey = "${clientLogicState.logicStatus?.time}"
        if (fetchKey == lastTraceFetchKey) {
            return
        }
        lastTraceFetchKey = fetchKey

        async {
            val present = props.clientLogicGlobal.tracedDocuments().isNotEmpty()
            setState {
                hasTrace = present
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onOptionsOpen() {
        val frameAtOpen = latestFrame
        setState {
            dropdownOpen = true
            frame = frameAtOpen
        }
    }


    private fun onOptionsClose() {
        setState {
            dropdownOpen = false
        }
    }


    private fun onTogglePauseOnError() {
        // NB: read the prior value OUTSIDE the setState lambda — the kzen setState lambda runs on an
        // empty object, so `pauseOnError = !pauseOnError` inside it would read undefined.
        val next = !state.pauseOnError
        setState {
            pauseOnError = next
        }
    }


    private fun onAction(action: String, active: Boolean, executing: Boolean) {
        val mainObjectLocation = this.mainObjectLocation
            ?: return

        when (action) {
            actionRunOrPause -> {
                if (executing) {
                    props.clientLogicGlobal.pauseAsync()
                }
                else if (active) {
                    props.clientLogicGlobal.continueRunAsync()
                }
                else {
                    props.clientLogicGlobal.startAndRunAsync(
                        mainObjectLocation, false, state.pauseOnError)
                }
            }

            actionStop -> {
                props.clientLogicGlobal.stopAsync()
            }

            actionStep -> {
                if (active) {
                    props.clientLogicGlobal.stepAsync()
                }
                else {
                    props.clientLogicGlobal.startAndRunAsync(
                        mainObjectLocation, true, state.pauseOnError)
                }
            }

            actionStepOver -> {
                // Only meaningful while paused mid-run (run a nested sub-document to completion); no
                // start-fresh path like Step.
                if (active) {
                    props.clientLogicGlobal.stepOverAsync()
                }
            }

            actionStepOut -> {
                // Only while paused mid-run: run the current document to its end and pause at the
                // caller's next step (or finish, at the run root).
                if (active) {
                    props.clientLogicGlobal.stepOutAsync()
                }
            }

            else -> {
                throw IllegalArgumentException("Unknown action: $action")
            }
        }

//        println("%%%% action: $action")
    }


    private fun onClear() {
        // Global: clear every retained trace, not just the focused document's.
        props.clientLogicGlobal.clearAllTracesAsync()
    }


    private fun onSlowToggle(stepOver: Boolean) {
        val mainObjectLocation = this.mainObjectLocation
            ?: return
        // Clicking the variant that is already looping turns slow-motion off; clicking the other
        // variant (or starting fresh) runs/switches into it.
        if (state.slowLooping && state.slowStepOver == stepOver) {
            props.clientLogicGlobal.pauseSlowAsync()
        }
        else {
            props.clientLogicGlobal.slowRunAsync(mainObjectLocation, state.pauseOnError, stepOver)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
//        val clientState = state.clientState
//            ?: return

        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
            }

            renderControls(/*clientState.clientLogicState*/)
        }

        renderDetailsOverlay()
    }


    private fun ChildrenBuilder.renderControls(
//        clientLogicState: ClientLogicState
    ) {
//        println("#### renderControls: ${clientLogicState.logicStatus?.active?.state}")

        val active = state.active
        val executing = state.executing
        val runnable = state.runnable

        // Transport: momentary actions (Step / Run-Pause / Stop) as an exclusive group.
        ToggleButtonGroup {
//                value = actionRun
            exclusive = true

            asDynamic()["onChange"] = { _, v ->
                // An exclusive group emits null when the active button is re-clicked (deselect); ignore it.
                val action = v as? String
                if (action != null) {
                    onAction(action, active, executing)
                }
            }

            if (!active && !runnable) {
                title = "Current document is not runnable"
                disabled = true
            }

            renderStepButton(active, executing, runnable)
            renderStepOverButton(active, executing)
            renderStepOutButton(active, executing)
            renderRunPauseButton(active, executing, runnable)
            renderStopButton(active)
        }

        renderControlsDivider()

        // Run modes: persistent on/off toggles. Kept OUT of the exclusive group so a deselect can't feed
        // the `v as String` cast above, and so they read as a distinct "how to run" cluster.
        renderSlowRunButton(active, executing, runnable)
        renderSlowStepOverButton(active, executing, runnable)
        renderPauseOnErrorToggle(active, runnable)

        renderControlsDivider()

        // Reset + inspect.
        renderClearButton(active)
        renderDetailsToggle(active)
    }


    private fun ChildrenBuilder.renderControlsDivider() {
        Divider {
            orientation = Orientation.vertical
            flexItem = true
            sx {
                marginLeft = 0.5.em
                marginRight = 0.25.em
            }
        }
    }


    private fun ChildrenBuilder.renderClearButton(active: Boolean) {
        ToggleButton {
            value = "clear"

            // Enabled whenever some trace is retained and nothing is running (Clear is global — see
            // onClear / hasTrace, which reflect ANY document's trace).
            disabled = active || !state.hasTrace
            size = Size.medium

            sx {
                height = 34.px
                marginLeft = 0.5.em
                color = NamedColor.black
            }

            title = "Clear all traces"

            // ToggleButton's onClick is (event, value) -> Unit; ignore both and just clear.
            onClick = { _, _ -> onClear() }

            span {
                css {
                    fontSize = 1.5.em
                    marginRight = 0.25.em
                    marginBottom = (-0.25).em
                }
                icon("material-symbols:replay") {}
            }
        }
    }


    private fun ChildrenBuilder.renderSlowRunButton(
        active: Boolean,
        executing: Boolean,
        runnable: Boolean
    ) {
        // Standalone toggle (not in the exclusive Step/Run/Stop group): slow-motion is a persistent
        // on/off state, like Pause-on-error, rather than a momentary action.
        ToggleButton {
            value = "slowRun"
            selected = state.slowLooping && !state.slowStepOver

            // Same availability as Step (start fresh, or continue from a pause); stays enabled while
            // looping so it can be toggled off.
            disabled = !(state.slowLooping || active && !executing || !active && runnable)
            size = Size.medium

            sx {
                height = 34.px
                marginLeft = 0.5.em
                color = NamedColor.black
            }

            title =
                if (state.slowLooping && !state.slowStepOver) {
                    "Pause slow-motion run"
                }
                else {
                    "Slow-motion run (auto-step, descends into nested logic)"
                }

            // ToggleButton's onClick is (event, value) -> Unit; ignore both and just toggle.
            onClick = { _, _ -> onSlowToggle(false) }

            span {
                css {
                    fontSize = 1.5.em
                    marginRight = 0.25.em
                    marginBottom = (-0.25).em
                }
                icon("material-symbols:slow-motion-video") {}
            }
        }
    }


    private fun ChildrenBuilder.renderSlowStepOverButton(
        active: Boolean,
        executing: Boolean,
        runnable: Boolean
    ) {
        // Slow-motion variant that auto-issues Step Over instead of Step: it paces step-by-step within
        // the current document without descending into nested logic.
        ToggleButton {
            value = "slowStepOver"
            selected = state.slowLooping && state.slowStepOver

            disabled = !(state.slowLooping || active && !executing || !active && runnable)
            size = Size.medium

            sx {
                height = 34.px
                marginLeft = 0.5.em
                color = NamedColor.black
            }

            title =
                if (state.slowLooping && state.slowStepOver) {
                    "Pause slow-motion run"
                }
                else {
                    "Slow-motion run, stepping over nested logic (stays in this document)"
                }

            onClick = { _, _ -> onSlowToggle(true) }

            span {
                css {
                    fontSize = 1.5.em
                    marginRight = 0.25.em
                    marginBottom = (-0.25).em
                }
                icon("material-symbols:autoplay") {}
            }
        }
    }


    private fun ChildrenBuilder.renderPauseOnErrorToggle(active: Boolean, runnable: Boolean) {
        ToggleButton {
            value = "pauseOnError"
            selected = state.pauseOnError

            // Applies at run start only, so lock it once a run is active.
            disabled = active || !runnable
            size = Size.medium

            sx {
                height = 34.px
                marginLeft = 0.5.em
                color = NamedColor.black
            }

            title = "Pause on error: stop at a failed step so it can be fixed and re-run"

            // ToggleButton's onClick is (event, value) -> Unit; we ignore both and just flip state.
            onClick = { _, _ -> onTogglePauseOnError() }

            span {
                css {
                    fontSize = 1.5.em
                    marginRight = 0.25.em
                    marginBottom = (-0.25).em
                }
                icon("material-symbols:warning") {}
            }
        }
    }


    private fun ChildrenBuilder.renderStepButton(
        active: Boolean,
        executing: Boolean,
        runnable: Boolean
    ) {
        ToggleButton {
            value = actionStep

            disabled = !(active && !executing || !active && runnable)

            size = Size.medium

            sx {
                height = 34.px
                color = NamedColor.black
            }

            title = "Step"

            span {
                css {
                    fontSize = 1.5.em
                    marginRight = 0.25.em
                    marginBottom = (-0.25).em
                }
                icon("material-symbols:redo") {}
            }
        }
    }


    private fun ChildrenBuilder.renderStepOverButton(
        active: Boolean,
        executing: Boolean
    ) {
        ToggleButton {
            value = actionStepOver

            // Only while paused mid-run: steps the current frame but runs any sub-document entered on
            // this step to completion instead of descending into it.
            disabled = !(active && !executing)

            size = Size.medium

            sx {
                height = 34.px
                color = NamedColor.black
            }

            title = "Step over (run nested sub-documents to completion)"

            span {
                css {
                    fontSize = 1.5.em
                    marginRight = 0.25.em
                    marginBottom = (-0.25).em
                }
                icon("material-symbols:step-over") {}
            }
        }
    }


    private fun ChildrenBuilder.renderStepOutButton(
        active: Boolean,
        executing: Boolean
    ) {
        ToggleButton {
            value = actionStepOut

            // Only while paused mid-run: run the current document to its end, pausing at the caller's
            // next step (or finish, at the run root).
            disabled = !(active && !executing)

            size = Size.medium

            sx {
                height = 34.px
                color = NamedColor.black
            }

            title = "Step out (run to end of current document)"

            span {
                css {
                    fontSize = 1.5.em
                    marginRight = 0.25.em
                    marginBottom = (-0.25).em
                }
                icon("material-symbols:step-out") {}
            }
        }
    }


    private fun ChildrenBuilder.renderRunPauseButton(
        active: Boolean,
        executing: Boolean,
        runnable: Boolean
    ) {
        ToggleButton {
            value = actionRunOrPause
            disabled = !active && !runnable
            size = Size.medium

            sx {
                height = 34.px
                color = NamedColor.black
            }

            if (executing) {
                title = "Pause"
            }
            else if (state.slowLooping && active) {
                // During slow-motion the loop holds the run Paused between steps; Run here means
                // "stop dwelling and finish at full speed" (continueRunAsync cancels the slow loop).
                title = "Run at full speed"
            }
            else if (runnable) {
                title =
                    if (active) {
                        "Continue running"
                    }
                    else {
                        "Run"
                    }
            }

            span {
                css {
                    fontSize = 1.5.em
                    marginRight = 0.25.em
                    marginBottom = (-0.25).em
                }
                if (executing) {
                    icon("material-symbols:pause") {}
                }
                else {
                    icon("material-symbols:play-arrow") {}
                }
            }
        }
    }


    private fun ChildrenBuilder.renderStopButton(active: Boolean) {
        ToggleButton {
            value = actionStop
            disabled = !active
            size = Size.medium

            sx {
                height = 34.px
                color = NamedColor.black
            }

            title = "Stop"

            span {
                css {
                    fontSize = 1.5.em
                    marginRight = 0.25.em
                    marginBottom = (-0.25).em
                }
                icon("material-symbols:stop") {}
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderDetailsToggle(active: Boolean) {
        span {
            ref = dropdownAnchorRef

            title =
                if (active) {
                    "Run details"
                }
                else {
                    "Nothing is running"
                }

            IconButton {
                sx {
                    if (active) {
                        color = NamedColor.black
                    }
                }

                disabled = !active

                if (active) {
                    onClick = { onOptionsOpen() }
                }

                icon("material-symbols:expand-more") {}
            }
        }
    }


    private fun ChildrenBuilder.renderDetailsOverlay() {
        Menu {
            open = state.dropdownOpen

            onClose = ::onOptionsClose

            anchorEl = dropdownAnchorRef.current?.let { { _ -> it } }

            div {
                css {
                    width = 16.em
                }

                val frame = state.frame

                if (frame == null) {
                    +"<Frame missing>"
                }
                else {
                    renderFrameTree(frame)
                }
            }
        }
    }


    private fun onNavigateToFrame(documentPath: DocumentPath) {
        props.navigationGlobal.goto(documentPath)
        onOptionsClose()
    }


    // The run's call stack as an indented tree of clickable rows; the deepest currently-executing
    // document (the active leaf) is marked. Clicking a row navigates to that document and closes the menu.
    private fun ChildrenBuilder.renderFrameTree(frame: LogicRunFrameInfo) {
        val activeLeaf = LogicRunFrames.deepestLeaf(frame).objectLocation
        renderFrameNode(frame, 0, activeLeaf)
    }


    private fun ChildrenBuilder.renderFrameNode(
        frame: LogicRunFrameInfo,
        depth: Int,
        activeLeaf: ObjectLocation
    ) {
        val documentPath = frame.objectLocation.documentPath
        val isActiveLeaf = frame.objectLocation == activeLeaf

        div {
            key = Key(frame.executionId.value)

            css {
                display = Display.flex
                alignItems = AlignItems.center
                paddingTop = 0.25.em
                paddingBottom = 0.25.em
                paddingLeft = (0.5 + depth * 1.0).em
                cursor = Cursor.pointer

                if (isActiveLeaf) {
                    fontWeight = FontWeight.bold
                }

                "&:hover" {
                    backgroundColor = Color("rgba(100, 159, 255, 0.18)")
                }
            }

            onClick = { onNavigateToFrame(documentPath) }

            if (isActiveLeaf) {
                span {
                    css {
                        display = Display.inlineFlex
                        alignItems = AlignItems.center
                        marginRight = 0.25.em
                    }
                    icon("material-symbols:play-arrow") {}
                }
            }

            +documentPath.name.value
        }

        for (dependency in frame.dependencies) {
            renderFrameNode(dependency, depth + 1, activeLeaf)
        }
    }


//    private fun ChildrenBuilder.renderSelected(
//            selected: DocumentPath?,
//            selectedFramePaths: List<DocumentPath>
//    ) {
//        if (selected == null) {
//            +"Please select a running script (below)"
//            return
//        }
//
//        +"Selected: ${selected.name}"
//
//        for (framePath in selectedFramePaths) {
//            div {
//                key = framePath.asString()
//                +framePath.name.value
//            }
//        }
//    }
//
//
//    private fun ChildrenBuilder.renderActiveSelection(
//            selected: DocumentPath?,
//            graphNotation: GraphNotation,
//            active: Set<DocumentPath>,
//            navigationRoute: NavigationRoute
//    ) {
//        val scriptDocuments = graphNotation
//                .documents
//                .values
//                .filter { ScriptDocument.isScript(/*it.key,*/ it.value) }
//
//        for (script in scriptDocuments) {
//            if (! active.contains(script.key) ||
//                    selected == script.key) {
//                continue
//            }
//
//            val pathValue = script.key.asString()
//
//            a {
//                css {
//                    color = Globals.inherit
//                    textDecoration = Globals.initial
//                    width = 100.pct
//                    height = 100.pct
//                }
//
//                key = pathValue
//                href = NavigationRoute(
//                    script.key,
//                    navigationRoute.requestParams.set(runningKey, pathValue)
//                ).toFragment()
//
//                MenuItem {
//                    +script.key.name.value
//                }
//            }
//        }
//    }
}