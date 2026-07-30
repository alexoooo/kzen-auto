package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import js.objects.unsafeJso
import react.ChildrenBuilder
import react.Key
import react.Props
import react.State
import react.dom.events.PointerEvent
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.script.model.scriptDependencyAnalysis
import tech.kzen.auto.client.objects.document.script.model.stepRowRefRegistry
import tech.kzen.auto.client.objects.document.script.step.header.StepHeader
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.logic.ClientLogicGlobal
import tech.kzen.auto.client.service.logic.LogicRunFrames
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptJumpAnalysis
import tech.kzen.auto.common.objects.document.script.model.ScriptNestingAnalysis
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.animations.requestAnimationFrame
import web.cssom.*
import web.dom.Element
import web.html.HTMLDivElement
import web.resize.ResizeObserver
import kotlin.math.abs


//---------------------------------------------------------------------------------------------------------------------
external interface ScriptExecutionMarginProps: Props {
    var clientStateGlobal: ClientStateGlobal

    // Action sink only — the margin reads all run/graph/breakpoint state from ClientState (via
    // clientStateGlobal), and calls moveToAsync / toggleBreakpointAsync. Never used to READ run state.
    var clientLogicGlobal: ClientLogicGlobal

    // Resolves the run's stable-id-keyed breakpoint set back to current locations (rename-tracked, so a dot
    // follows its renamed step).
    var objectStableMapper: ObjectStableMapper
}


// One margin row: an executable step whose row is registered, its container-relative "line" (the vertical
// centre of the step's header row — see StepHeader.stepHeaderRowAttribute), and whether it holds a breakpoint.
data class ScriptMarginRow(
    val location: ObjectLocation,
    val anchorTopPx: Double,
    val breakpoint: Boolean
)


external interface ScriptExecutionMarginState: State {
    // Every executable step of the viewed document whose row is mounted, in document order. Rebuilt from
    // scratch each remeasure, so the setState guard compares by VALUE (data class list equality), never by
    // reference.
    var rows: List<ScriptMarginRow>

    // The run's next-to-run step; null hides the glyph (no live frame in this document). Independent of the
    // bands: breakpoints render with no run at all.
    var arrowLocation: ObjectLocation?

    // True only while the run is settled (a stepped boundary, ExplicitPaused or ErrorPaused) AND this document
    // has a live frame — the arrow is draggable in whichever frame is being viewed, nested or root. The server
    // then refuses the moves it can't carry out (a loop-hosted frame, a non-repositionable hop), which the
    // client surfaces as the move rejection message.
    var draggable: Boolean

    var dragging: Boolean

    // Live drop candidate during a drag (the step whose anchor line is nearest the cursor), plus its
    // validity/warn styling and its container-relative row rect for the highlight band.
    var candidate: ObjectLocation?
    var candidateValid: Boolean
    var candidateWarn: Boolean
    var candidateTopPx: Double?
    var candidateHeightPx: Double?
}


//---------------------------------------------------------------------------------------------------------------------
// The Script's execution margin: the IDE/VBA gutter column down the left of the step list, and the single home
// of every execution-control affordance (step headers hold none). It renders a breakpoint band per executable
// step — click to toggle, the unset dot hover-revealed in pure CSS — plus the draggable next-to-run arrow, both
// anchored on the step's HEADER row rather than the card's vertical middle.
//
// Modeled on ScriptDependencyOverlay: an absolute inset:0 sibling over ScriptController.render's relative
// container, observing ClientStateGlobal + StepRowRefRegistry and measuring row rects on an rAF. The container
// reserves the strip with paddingLeft (scriptExecutionMarginWidthPx) — absolutely positioned children resolve
// against the PADDING box, so left:0 here is the strip's left edge while flow content starts to its right. It
// never alters the flex-row layout the dependency overlay's anchoring depends on. Drag uses a transient fixed
// drop-surface to capture move/up viewport-wide (no HTML5 DnD, no pointer-capture races across re-renders).
class ScriptExecutionMargin(
    props: ScriptExecutionMarginProps
):
    RPureComponent<ScriptExecutionMarginProps, ScriptExecutionMarginState>(props),
    ClientStateGlobal.DocumentScopedObserver
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Reserved by ScriptController as paddingLeft on the step stage — carved OUT of its former left margin,
        // so card positions are unchanged and only this strip is new.
        const val scriptExecutionMarginWidthPx = 22

        private val arrowColour = Color("#f9a825")
        private val breakpointColour = Color("#c62828")
        private val validColour = Color("#1976d2")
        private val invalidColour = Color("#9e9e9e")
        private val warnColour = Color("#ed6c02")

        private const val glyphSizePx = 16.0
        private const val breakpointDotPx = 12.0

        // A breakpoint on the next-to-run step renders as a RING instead of a filled dot, so the arrow painted
        // over it still reads (the classic debugger stacking) rather than hiding it.
        private const val breakpointRingPx = 20.0

        // Fixed-height hit band centred on the step's anchor — deliberately NOT the row height: a container
        // step's registered row DOM-contains every nested step's row, so full-height bands would overlap and
        // the container's would swallow the clicks meant for its children.
        private const val bandHeightPx = 22.0

        // Fallback anchor when a row carries no header marker: the leaf card's header centre (0.5em card
        // padding + half of StepHeader's 40px run icon). Every StepHeader host marks its top row, so this is
        // only reached for a row shape that has no header at all.
        private const val fallbackAnchorOffsetPx = 28.0

        // Below this much pointer travel the drag is treated as a click instead: the glyph covers the band of
        // the step it sits on, so without this that step's breakpoint would be the one in the document that
        // can't be toggled.
        private const val dragSlopPx = 4.0

        private const val breakpointDotAttribute = "data-breakpoint-dot"
        private const val breakpointDotSet = "set"
        private const val breakpointDotUnset = "unset"
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val rootRef = createRef<HTMLDivElement>()
    private var unsubscribeRegistry: (() -> Unit)? = null
    private var resizeObserver: ResizeObserver? = null
    private var observedElement: HTMLDivElement? = null
    private var latestClientState: ClientState? = null
    private var rafScheduled: Boolean = false

    // Single-entry memo of the document's executable steps, keyed on the GraphDefinitionAttempt REFERENCE (the
    // ScriptStore.dependencyAnalysis idiom): remeasure runs on every publish, and a logic-status publish reuses
    // the same attempt, so the run hot path never repeats the notation walk.
    private var executableStepsAttempt: GraphDefinitionAttempt? = null
    private var executableStepsPath: DocumentPath? = null
    private var executableStepsCached: List<ObjectLocation> = listOf()

    // Drag session, precomputed once at pointer-down (the step structure is static while paused). The addressed
    // frame is part of that snapshot: a status refresh landing mid-drag would otherwise retarget the move at
    // whichever invocation is live by pointer-up.
    private var dragExecutionId: LogicExecutionId? = null
    private var dragNextToRun: ObjectLocation? = null
    private var dragHitRows: List<ObjectLocation> = listOf()
    private var dragValidTargets: Set<ObjectLocation> = setOf()
    private var dragWarnTargets: Set<ObjectLocation> = setOf()
    private var dragOriginY: Double = 0.0
    private var dragMoved: Boolean = false


    //-----------------------------------------------------------------------------------------------------------------
    init {
        // Reaches ScriptStore's memoized dependency analysis (see scriptDependencyAnalysis) and the shared
        // step-row rect registry (see stepRowRefRegistry).
        installContextType(DocumentBridgeContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ScriptExecutionMarginState.init(props: ScriptExecutionMarginProps) {
        rows = listOf()
        arrowLocation = null
        draggable = false
        dragging = false
        candidate = null
        candidateValid = false
        candidateWarn = false
        candidateTopPx = null
        candidateHeightPx = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
        unsubscribeRegistry = stepRowRefRegistry()?.observe { scheduleRemeasure() }
        attachResizeObserver()
    }


    override fun componentDidUpdate(
        prevProps: ScriptExecutionMarginProps,
        prevState: ScriptExecutionMarginState,
        snapshot: Any
    ) {
        attachResizeObserver()
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
        unsubscribeRegistry?.invoke()
        unsubscribeRegistry = null
        resizeObserver?.disconnect()
        resizeObserver = null
        observedElement = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun attachResizeObserver() {
        val element = rootRef.current
        if (element === observedElement) {
            return
        }
        resizeObserver?.disconnect()
        observedElement = element
        if (element == null) {
            resizeObserver = null
            return
        }
        // NB: this root is inset:0, so its height IS the stage's — expanding or collapsing a step (ScriptStore
        //     state, which this component deliberately doesn't observe) changes the stage height and re-anchors
        //     every band through here, the same mechanism ScriptDependencyOverlay already relies on.
        val observer = ResizeObserver { _, _ -> scheduleRemeasure() }
        observer.observe(element)
        resizeObserver = observer
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        latestClientState = clientState
        scheduleRemeasure()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun scheduleRemeasure() {
        // Defer to the next animation frame so layout has settled (same reasoning as ScriptDependencyOverlay).
        if (rafScheduled) {
            return
        }
        rafScheduled = true
        requestAnimationFrame {
            rafScheduled = false
            remeasure()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun remeasure() {
        // Never reposition mid-drag — a status poll landing during a drag would jump the glyph around.
        if (state.dragging) {
            return
        }

        val clientState = latestClientState
            ?: return clear()
        val container = rootRef.current
            ?: return

        val documentPath = clientState.navigationRoute.documentPath
            ?: return clear()
        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
            ?: return clear()
        if (!ScriptConventions.isScript(documentNotation)) {
            return clear()
        }

        val registry = stepRowRefRegistry()
            ?: return clear()

        // O(breakpoints) rather than O(steps): map the stable-id set to current locations ONCE, instead of
        // minting a stable id per step on every publish.
        val breakpointLocations = clientState
            .clientLogicState
            .breakpoints
            .mapNotNull { props.objectStableMapper.objectLocationOrNull(it) }
            .toHashSet()

        val containerRect = container.getBoundingClientRect()
        val newRows = executableStepLocations(clientState, documentPath).mapNotNull { location ->
            val rowElement = registry.get(location)
                ?: return@mapNotNull null  // row not registered yet — the registry observer re-fires on mount

            ScriptMarginRow(
                location,
                anchorTopPx(rowElement, containerRect.top),
                location in breakpointLocations)
        }

        val frame = clientState.clientLogicState.logicStatus?.active?.frame
        val documentFrame = LogicRunFrames.frameForDocument(frame, documentPath)
        val nextToRun = documentFrame?.position

        // Draggable while the run is SETTLED (paused at any boundary — a manual step settle, a Pause step,
        // or error-park) — the same "!running && !stepping" gate the server's moveTo enforces; isHaltPaused()
        // would be too strict (it excludes a plain stepped Paused). Any document with a live frame qualifies:
        // whether that frame can actually be repositioned is the server's call, answered per drop.
        val logicState = clientState.clientLogicState
        val canDrag = logicState.isActive() && !logicState.isExecuting() && documentFrame != null

        update(newRows, nextToRun, canDrag)
    }


    // The step's "line": the vertical centre of its StepHeader top row, relative to [containerTop].
    // querySelector is document-first, so a container step's row resolves to its OWN header rather than the
    // header of a step nested inside it.
    private fun anchorTopPx(rowElement: Element, containerTop: Double): Double {
        val headerRow = rowElement.querySelector("[${StepHeader.stepHeaderRowAttribute}]")
        if (headerRow != null) {
            val headerRect = headerRow.getBoundingClientRect()
            return headerRect.top - containerTop + headerRect.height / 2.0
        }
        return rowElement.getBoundingClientRect().top - containerTop + fallbackAnchorOffsetPx
    }


    private fun executableStepLocations(
        clientState: ClientState,
        documentPath: DocumentPath
    ): List<ObjectLocation> {
        val graphDefinitionAttempt = clientState.graphDefinitionAttempt
        if (executableStepsAttempt === graphDefinitionAttempt && executableStepsPath == documentPath) {
            return executableStepsCached
        }

        val computed = ScriptNestingAnalysis
            .orderedExecutableStepPaths(
                clientState.graphStructure().graphNotation,
                documentPath,
                ScriptTree.read(documentPath, graphDefinitionAttempt.successful()))
            .map { ObjectLocation(documentPath, it) }

        executableStepsAttempt = graphDefinitionAttempt
        executableStepsPath = documentPath
        executableStepsCached = computed
        return computed
    }


    private fun clear() {
        update(listOf(), null, false)
    }


    private fun update(newRows: List<ScriptMarginRow>, nextToRun: ObjectLocation?, canDrag: Boolean) {
        if (state.rows == newRows && state.arrowLocation == nextToRun && state.draggable == canDrag) {
            return
        }
        setState {
            rows = newRows
            arrowLocation = nextToRun
            draggable = canDrag
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onToggleBreakpoint(location: ObjectLocation) {
        // Single source of truth: the stable-id-keyed registry in ClientLogicGlobal (pushed to the server
        // there); the resulting publish flows back through onClientState to repaint the dot.
        props.clientLogicGlobal.toggleBreakpointAsync(location)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onGlyphPointerDown(event: PointerEvent<HTMLDivElement>) {
        if (!state.draggable) {
            return
        }
        event.stopPropagation()
        event.preventDefault()

        val clientState = latestClientState
            ?: return
        val documentPath = clientState.navigationRoute.documentPath
            ?: return
        val frame = clientState.clientLogicState.logicStatus?.active?.frame
        val documentFrame = LogicRunFrames.frameForDocument(frame, documentPath)
            ?: return
        val nextToRun = documentFrame.position
            ?: return
        val registry = stepRowRefRegistry()
            ?: return

        val notation = clientState.graphStructure().graphNotation
        val tree = ScriptTree.read(documentPath, clientState.graphDefinitionAttempt.successful())

        val ordered = executableStepLocations(clientState, documentPath)
        val orderedIndex = ordered.withIndex().associate { (index, location) -> location.objectPath to index }
        val nextIndex = orderedIndex[nextToRun.objectPath] ?: -1

        val hitRows = ordered.filter { registry.get(it) != null }

        val validTargets = hitRows
            .filter { ScriptJumpAnalysis.isValidTarget(notation, documentPath, tree, it.objectPath) }
            .toSet()

        // Trace-free advisory (decision 2): warn for a FORWARD jump that would skip a producer a step
        // running at/after the target still depends on — the source is a path-predecessor of the target
        // (so it's skipped) that also sits after the current position (a genuine forward skip, not an
        // already-run earlier step), and the consumer is at/after the target (drop set → re-runs). Purely
        // advisory (never disables); the real backstop is the runtime "No value produced" error-park.
        val edges = scriptDependencyAnalysis(clientState, documentPath).edges
        val warnTargets = validTargets
            .filter { target ->
                val plan = ScriptJumpAnalysis.plan(notation, documentPath, tree, target.objectPath)
                val skipped = plan.precedingOnPath.toHashSet()
                edges.any { edge ->
                    val source = edge.source.objectPath
                    source in skipped &&
                        (orderedIndex[source] ?: -1) >= nextIndex &&
                        edge.target.objectPath in plan.dropSet
                }
            }
            .toSet()

        dragExecutionId = documentFrame.executionId
        dragNextToRun = nextToRun
        dragHitRows = hitRows
        dragValidTargets = validTargets
        dragWarnTargets = warnTargets
        dragOriginY = event.clientY
        dragMoved = false

        setState {
            dragging = true
            candidate = null
            candidateValid = false
            candidateWarn = false
            candidateTopPx = null
            candidateHeightPx = null
        }
    }


    private fun onSurfacePointerMove(event: PointerEvent<HTMLDivElement>) {
        if (!state.dragging) {
            return
        }
        if (!dragMoved && abs(event.clientY - dragOriginY) <= dragSlopPx) {
            // Still within the slop — a click on the glyph, not a drag (see onSurfacePointerUp).
            return
        }
        dragMoved = true

        val container = rootRef.current
            ?: return

        val candidateLocation = nearestStepAnchor(event.clientY)
        if (candidateLocation == null) {
            clearCandidate()
            return
        }

        val rowElement = stepRowRefRegistry()?.get(candidateLocation)
        if (rowElement == null) {
            clearCandidate()
            return
        }

        val containerRect = container.getBoundingClientRect()
        val rowRect = rowElement.getBoundingClientRect()
        val topPx = rowRect.top - containerRect.top
        val heightPx = rowRect.height
        val valid = candidateLocation in dragValidTargets
        val warn = candidateLocation in dragWarnTargets

        if (state.candidate == candidateLocation &&
                state.candidateValid == valid &&
                state.candidateWarn == warn &&
                state.candidateTopPx == topPx &&
                state.candidateHeightPx == heightPx) {
            return
        }

        setState {
            candidate = candidateLocation
            candidateValid = valid
            candidateWarn = warn
            candidateTopPx = topPx
            candidateHeightPx = heightPx
        }
    }


    private fun onSurfacePointerUp(event: PointerEvent<HTMLDivElement>) {
        event.stopPropagation()

        val target = state.candidate
        val valid = state.candidateValid
        val nextToRun = dragNextToRun
        val executionId = dragExecutionId
        val moved = dragMoved

        dragExecutionId = null
        dragNextToRun = null
        dragHitRows = listOf()
        dragValidTargets = setOf()
        dragWarnTargets = setOf()
        dragMoved = false

        setState {
            dragging = false
            candidate = null
            candidateValid = false
            candidateWarn = false
            candidateTopPx = null
            candidateHeightPx = null
        }

        if (!moved) {
            // A click, not a drag. The glyph covers the next-to-run step's breakpoint band and swallows its
            // click, so route it back here — otherwise the step execution is parked on would be the one step
            // whose breakpoint can't be toggled.
            if (nextToRun != null) {
                onToggleBreakpoint(nextToRun)
            }
            return
        }

        if (target != null && valid && target != nextToRun && executionId != null) {
            props.clientLogicGlobal.moveToAsync(target, executionId)
        }
    }


    private fun clearCandidate() {
        if (state.candidate == null) {
            return
        }
        setState {
            candidate = null
            candidateValid = false
            candidateWarn = false
            candidateTopPx = null
            candidateHeightPx = null
        }
    }


    // The step whose ANCHOR LINE is nearest the cursor. Deliberately not rect containment (what the pre-margin
    // arrow did): a container step's registered row DOM-contains every nested step's row and comes first in
    // document order, so "first row containing clientY" could never resolve to a step inside an If branch — a
    // legal move-to target. Anchors are points, so nesting doesn't overlap and the nearest one always wins.
    private fun nearestStepAnchor(clientY: Double): ObjectLocation? {
        val registry = stepRowRefRegistry()
            ?: return null

        var best: ObjectLocation? = null
        var bestDistance = Double.MAX_VALUE
        for (location in dragHitRows) {
            val element = registry.get(location)
                ?: continue
            // Viewport-relative (containerTop = 0), matching clientY.
            val anchorY = anchorTopPx(element, 0.0)
            val distance = abs(clientY - anchorY)
            if (distance < bestDistance) {
                bestDistance = distance
                best = location
            }
        }
        return best
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            ref = rootRef
            css {
                position = Position.absolute
                top = 0.px
                left = 0.px
                right = 0.px
                bottom = 0.px
                // The overlay itself is transparent to the mouse; only the margin column (and, while
                // dragging, the drop surface) opts back in, so cards below stay clickable.
                pointerEvents = None.none
            }

            renderMarginColumn()

            if (state.dragging) {
                renderCandidateHighlight()
            }

            if (state.dragging) {
                renderDragSurface()
            }
        }
    }


    // The gutter column itself: an invisible full-height strip exactly as wide as the reserved padding, whose
    // only job beyond hosting the bands and the glyph is to be ONE hover target. Revealing every unset dot the
    // moment the cursor enters the margin — rather than only the dot directly under it — is what makes
    // breakpoints discoverable; per-band-only reveal hides the affordance until you happen to hover a step's
    // exact line. Pure CSS, per the ScriptStepSlot drag-handle idiom (no React hover state).
    private fun ChildrenBuilder.renderMarginColumn() {
        div {
            css {
                position = Position.absolute
                top = 0.px
                left = 0.px
                bottom = 0.px
                width = scriptExecutionMarginWidthPx.px
                // Re-enabled for the column and its descendants: the strip covers only the reserved padding,
                // where nothing else is interactive, so capturing hover here costs no click elsewhere.
                pointerEvents = Auto.auto

                // Scoped to the UNSET dot, so a set one keeps full opacity under hover. The short lead-in
                // delay is what keeps a cursor sweeping past the margin from flashing the whole column: the
                // fade only starts once the cursor has settled, and is imperceptible as lag on a real hover.
                "&:hover [$breakpointDotAttribute='$breakpointDotUnset']" {
                    opacity = number(0.3)
                    transition = "opacity 160ms ease-in 60ms".unsafeCast<Transition>()
                }
            }

            // Bands first, so the arrow — rendered after them — paints over the band it shares a line with.
            for (row in state.rows) {
                renderBreakpointBand(row)
            }

            renderGlyph()
        }
    }


    private fun ChildrenBuilder.renderBreakpointBand(row: ScriptMarginRow) {
        val ring = row.breakpoint && row.location == state.arrowLocation
        val dotSize = if (ring) { breakpointRingPx } else { breakpointDotPx }

        div {
            key = Key(row.location.asString())

            css {
                position = Position.absolute
                top = (row.anchorTopPx - bandHeightPx / 2.0).px
                left = 0.px
                width = 100.pct
                height = bandHeightPx.px
                display = Display.flex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                cursor = Cursor.pointer

                // The column reveals every unset dot at 0.3; the one about to be clicked comes up further so
                // the click target is unambiguous. `&&` doubles the generated class to out-specify the
                // column's rule, which targets the same dots. No lead-in delay here — by the time a band is
                // hovered the column's reveal has already committed, so this step is immediate.
                "&&:hover [$breakpointDotAttribute='$breakpointDotUnset']" {
                    opacity = number(0.6)
                    transition = "opacity 100ms ease-out".unsafeCast<Transition>()
                }
            }

            title = if (row.breakpoint) { "Remove breakpoint" } else { "Add breakpoint" }

            // No stopPropagation needed: the band is a sibling of the step cards, not a descendant, so this
            // click can't also reach a card's click-to-expand.
            onClick = { onToggleBreakpoint(row.location) }

            div {
                asDynamic()[breakpointDotAttribute] =
                    if (row.breakpoint) { breakpointDotSet } else { breakpointDotUnset }

                css {
                    width = dotSize.px
                    height = dotSize.px
                    boxSizing = BoxSizing.borderBox
                    borderRadius = 50.pct
                    if (ring) {
                        // Hollow, so the arrow painted over it stays readable inside the ring.
                        border = Border(2.px, LineStyle.solid, breakpointColour)
                    }
                    else {
                        backgroundColor = breakpointColour
                    }
                    opacity = number(if (row.breakpoint) 1.0 else 0.0)

                    // Resting transition — governs the fade OUT (and a toggled dot's own appear/disappear).
                    // Faster than the reveal and undelayed, so leaving the margin clears it promptly.
                    transition = "opacity 120ms ease-out".unsafeCast<Transition>()
                }
            }
        }
    }


    private fun ChildrenBuilder.renderGlyph() {
        // While dragging, the glyph follows the cursor SNAPPED to the candidate's line (the VB idiom — the
        // marker itself moves), holding its parked position until the first candidate resolves.
        val anchorLocation =
            if (state.dragging) { state.candidate ?: state.arrowLocation }
            else { state.arrowLocation }

        val topPx = state.rows.firstOrNull { it.location == anchorLocation }?.anchorTopPx
            ?: return

        div {
            css {
                position = Position.absolute
                top = (topPx - glyphSizePx / 2.0).px
                left = ((scriptExecutionMarginWidthPx - glyphSizePx) / 2.0).px
                width = glyphSizePx.px
                height = glyphSizePx.px
                display = Display.flex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                color = arrowColour

                if (state.draggable) {
                    pointerEvents = Auto.auto
                    cursor = if (state.dragging) Cursor.grabbing else Cursor.grab
                    opacity = number(1.0)
                }
                else {
                    // Present but inert while the run is executing (not settled-paused) — mirrors the
                    // next-to-run border, which also shows during a run. pointerEvents:none also lets a click
                    // fall through to the breakpoint band underneath.
                    pointerEvents = None.none
                    opacity = number(0.5)
                }
            }

            if (state.draggable) {
                title = "Drag to set the next step"
                onPointerDown = { onGlyphPointerDown(it) }
            }

            icon("material-symbols:play-arrow") {
                style = unsafeJso {
                    fontSize = glyphSizePx.px
                }
            }
        }
    }


    private fun ChildrenBuilder.renderCandidateHighlight() {
        val topPx = state.candidateTopPx
            ?: return
        val heightPx = state.candidateHeightPx
            ?: return

        val colour = when {
            !state.candidateValid -> invalidColour
            state.candidateWarn -> warnColour
            else -> validColour
        }

        div {
            css {
                position = Position.absolute
                top = topPx.px
                // Frames the step cards, starting where the margin ends.
                left = scriptExecutionMarginWidthPx.px
                right = 0.px
                height = heightPx.px
                boxSizing = BoxSizing.borderBox
                border = Border(2.px, LineStyle.solid, colour)
                borderRadius = 3.px
                pointerEvents = None.none
            }
        }
    }


    private fun ChildrenBuilder.renderDragSurface() {
        // A transient viewport-wide surface that captures pointer move/up for the duration of the drag, so
        // the drop resolves no matter what the cursor is over. Removed the instant the drag ends.
        div {
            css {
                position = Position.fixed
                top = 0.px
                left = 0.px
                right = 0.px
                bottom = 0.px
                zIndex = integer(2000)
                pointerEvents = Auto.auto
                cursor = Cursor.grabbing
            }
            onPointerMove = { onSurfacePointerMove(it) }
            onPointerUp = { onSurfacePointerUp(it) }
        }
    }
}
