package tech.kzen.auto.client.objects.ribbon

import emotion.react.css
import mui.material.*
import mui.material.Size
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
import tech.kzen.auto.client.util.DefinitionErrors
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.exec.logic.run.model.LogicRunFrameInfo
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

    // When set, a failed step pauses the run (to fix + continue) instead of ending it. A live toggle: it
    // seeds the value at run start AND can be flipped while the run is paused, pushing the new value onto the
    // active run (onTogglePauseOnError -> setPauseOnErrorAsync). Disabled only while actively executing.
    var pauseOnError: Boolean

    // Whether the client-paced "slow motion" auto-step loop is currently driving the run.
    var slowLooping: Boolean

    // While slowLooping, whether the loop is the Step-Over variant (stays within the current document)
    // rather than the Step variant (descends into nested logic).
    var slowStepOver: Boolean

    // When the current (logic) document can't run because it — or a transitive dependency — failed to define,
    // the reason, shown as the disabled run-controls tooltip. Null when the document is runnable (or isn't a
    // logic document); folded into `runnable` so all the run controls disable without per-button changes.
    var runBlockReason: String?
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
        runBlockReason = null
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

        // A logic document whose root (or a transitive dependency) failed to define can't run — surface the
        // reason and treat it as not-runnable so every run control disables, instead of letting the run fail
        // opaquely on the server.
        val runBlocker =
            if (isLogic) {
                DefinitionErrors.runBlocker(clientState.graphDefinitionAttempt, mainLocation)
            }
            else {
                null
            }

        val dropdownWasOpen = state.dropdownOpen
        setState {
            runnable = isLogic && runBlocker == null
            runBlockReason = runBlocker
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

        // While a run is active (paused), push the new value onto the running control so the next continue /
        // step honours it; while idle the toggle just seeds the next start (startAndRunAsync passes it).
        if (state.active) {
            props.clientLogicGlobal.setPauseOnErrorAsync(next)
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

        // Two compact rows stacked at the top-right (right-aligned). This is a floated cluster, so the
        // ribbon's tabs and sub-action buttons flow around it — filling the width beside it on the rows it
        // covers, and reclaiming the full header width below it. The header is a BFC (flowRoot) so the
        // taller-than-one-row cluster stays contained.
        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = AlignItems.flexEnd
            }

            // Row 1 — transport: momentary actions (Step / Step Over / Step Out / Run-Pause / Stop) as an
            // exclusive group.
            ToggleButtonGroup {
//                value = actionRun
                exclusive = true
                size = Size.small

                asDynamic()["onChange"] = { _, v ->
                    // An exclusive group emits null when the active button is re-clicked (deselect); ignore it.
                    val action = v as? String
                    if (action != null) {
                        onAction(action, active, executing)
                    }
                }

                if (!active && !runnable) {
                    // A definition failure gives a specific reason; otherwise it's simply not a logic document.
                    title = state.runBlockReason ?: "Current document is not runnable"
                    disabled = true
                }

                renderStepButton(active, executing, runnable)
                renderStepOverButton(active, executing)
                renderStepOutButton(active, executing)
                renderRunPauseButton(active, executing, runnable)
                renderStopButton(active)
            }

            // Row 2 — run modes (persistent on/off toggles, kept OUT of the exclusive group so a deselect
            // can't feed the `v as String` cast above) plus reset + inspect.
            div {
                css {
                    display = Display.flex
                    alignItems = AlignItems.center
                    marginTop = 0.25.em
                }

                renderSlowRunButton(active, executing, runnable)
                renderSlowStepOverButton(active, executing, runnable)
                renderPauseOnErrorToggle(active, executing, runnable)

                renderControlsDivider()

                renderClearButton(active)
                renderDetailsToggle(active)
            }
        }
    }


    private fun ChildrenBuilder.renderControlsDivider() {
        Divider {
            orientation = Orientation.vertical
            flexItem = true
            sx {
                marginLeft = 0.25.em
                marginRight = 0.25.em
            }
        }
    }


    // Compact icon for a header run-control button: smaller than the former 1.5em and with no right
    // margin (these buttons are icon-only), to keep the single-row control cluster narrow.
    private fun ChildrenBuilder.controlIcon(name: String) {
        span {
            css {
                fontSize = 1.2.em
                marginBottom = (-0.2).em
            }
            icon(name) {}
        }
    }


    private fun ChildrenBuilder.renderClearButton(active: Boolean) {
        ToggleButton {
            value = "clear"

            // Enabled whenever some trace is retained and nothing is running (Clear is global — see
            // onClear / hasTrace, which reflect ANY document's trace).
            disabled = active || !state.hasTrace
            size = Size.small

            sx {
                height = 30.px
                marginLeft = 0.25.em
                color = NamedColor.black
            }

            title = "Clear all traces"

            // ToggleButton's onClick is (event, value) -> Unit; ignore both and just clear.
            onClick = { _, _ -> onClear() }

            controlIcon("material-symbols:replay")
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
            size = Size.small

            sx {
                height = 30.px
                marginLeft = 0.25.em
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

            controlIcon("material-symbols:slow-motion-video")
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
            size = Size.small

            sx {
                height = 30.px
                marginLeft = 0.25.em
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

            controlIcon("material-symbols:autoplay")
        }
    }


    private fun ChildrenBuilder.renderPauseOnErrorToggle(active: Boolean, executing: Boolean, runnable: Boolean) {
        ToggleButton {
            value = "pauseOnError"
            selected = state.pauseOnError

            // A live toggle: clickable while paused (so it can be turned on/off mid-run and pushed to the
            // active run) and while idle on a runnable document (to seed the next start); locked only while
            // actively executing or on a non-runnable idle document.
            disabled = executing || (!active && !runnable)
            size = Size.small

            sx {
                height = 30.px
                marginLeft = 0.25.em
                color = NamedColor.black
            }

            title = "Pause on error: stop at a failed step so it can be fixed and re-run"

            // ToggleButton's onClick is (event, value) -> Unit; we ignore both and just flip state.
            onClick = { _, _ -> onTogglePauseOnError() }

            controlIcon("material-symbols:warning")
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

            size = Size.small

            sx {
                height = 30.px
                color = NamedColor.black
            }

            title = "Step"

            controlIcon("material-symbols:redo")
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

            size = Size.small

            sx {
                height = 30.px
                color = NamedColor.black
            }

            title = "Step over (run nested sub-documents to completion)"

            controlIcon("material-symbols:step-over")
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

            size = Size.small

            sx {
                height = 30.px
                color = NamedColor.black
            }

            title = "Step out (run to end of current document)"

            controlIcon("material-symbols:step-out")
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
            size = Size.small

            sx {
                height = 30.px
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

            if (executing) {
                controlIcon("material-symbols:pause")
            }
            else {
                controlIcon("material-symbols:play-arrow")
            }
        }
    }


    private fun ChildrenBuilder.renderStopButton(active: Boolean) {
        ToggleButton {
            value = actionStop
            disabled = !active
            size = Size.small

            sx {
                height = 30.px
                color = NamedColor.black
            }

            title = "Stop"

            controlIcon("material-symbols:stop")
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
                size = Size.small

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