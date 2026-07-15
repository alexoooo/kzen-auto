package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import js.objects.unsafeJso
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.events.PointerEvent
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.script.display.dependency.StepRowRefRegistry
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.logic.ClientLogicGlobal
import tech.kzen.auto.client.service.logic.LogicRunFrames
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptDependencyAnalysis
import tech.kzen.auto.common.objects.document.script.model.ScriptJumpAnalysis
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import web.animations.requestAnimationFrame
import web.cssom.*
import web.html.HTMLDivElement
import web.resize.ResizeObserver
import kotlin.math.abs


//---------------------------------------------------------------------------------------------------------------------
external interface ScriptMoveToArrowProps: Props {
    var clientStateGlobal: ClientStateGlobal

    // Action sink only — the arrow reads all run/graph state from ClientState (via clientStateGlobal),
    // and calls moveToAsync on drop / fallback. Never used to READ run state.
    var clientLogicGlobal: ClientLogicGlobal
}


external interface ScriptMoveToArrowState: State {
    // Vertical center of the next-to-run row, container-relative; null hides the glyph (no live frame in
    // this document, or its row isn't registered yet).
    var arrowTopPx: Double?

    // True only while the run is settled (ExplicitPaused / ErrorPaused) AND its ROOT frame is this document,
    // matching the server move-to gate (a nested sub-script frame shows the glyph but can't be dragged v1).
    var draggable: Boolean

    var dragging: Boolean

    // Live drop candidate during a drag (the step row nearest the cursor), plus its validity/warn styling
    // and its container-relative rect for the highlight band.
    var candidate: ObjectLocation?
    var candidateValid: Boolean
    var candidateWarn: Boolean
    var candidateTopPx: Double?
    var candidateHeightPx: Double?
}


//---------------------------------------------------------------------------------------------------------------------
// Draggable VB/IDE-style "next-to-run" arrow (execution-control phase XC3, the primary move-to affordance).
// A single positional marker at the run's next-to-run step, in a narrow strip hugging the step cards' left
// edge; while the run is settled-paused it can be dragged onto another step to reposition execution
// (ClientLogicGlobal.moveToAsync). Modeled on ScriptDependencyOverlay: an absolute inset:0 sibling over
// ScriptController.renderMain's relative container, observing ClientStateGlobal + StepRowRefRegistry and
// measuring row rects on an rAF. It never alters the flex-row layout the dependency overlay's anchoring
// depends on. Drag uses a transient fixed drop-surface to capture move/up viewport-wide (no HTML5 DnD,
// no pointer-capture races across re-renders).
class ScriptMoveToArrow(
    props: ScriptMoveToArrowProps
):
    RPureComponent<ScriptMoveToArrowProps, ScriptMoveToArrowState>(props),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val arrowColour = Color("#f9a825")
        private val validColour = Color("#1976d2")
        private val invalidColour = Color("#9e9e9e")
        private val warnColour = Color("#ed6c02")

        private const val glyphSizePx = 18.0
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val rootRef = createRef<HTMLDivElement>()
    private var unsubscribeRegistry: (() -> Unit)? = null
    private var resizeObserver: ResizeObserver? = null
    private var observedElement: HTMLDivElement? = null
    private var latestClientState: ClientState? = null
    private var rafScheduled: Boolean = false

    // Drag session, precomputed once at pointer-down (the step structure is static while paused).
    private var dragNextToRun: ObjectLocation? = null
    private var dragHitRows: List<ObjectLocation> = listOf()
    private var dragValidTargets: Set<ObjectLocation> = setOf()
    private var dragWarnTargets: Set<ObjectLocation> = setOf()


    //-----------------------------------------------------------------------------------------------------------------
    override fun ScriptMoveToArrowState.init(props: ScriptMoveToArrowProps) {
        arrowTopPx = null
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
        unsubscribeRegistry = StepRowRefRegistry.observe { scheduleRemeasure() }
        attachResizeObserver()
    }


    override fun componentDidUpdate(
        prevProps: ScriptMoveToArrowProps,
        prevState: ScriptMoveToArrowState,
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
        // Never reposition the glyph mid-drag — a status poll landing during a drag would jump it around.
        if (state.dragging) {
            return
        }

        val clientState = latestClientState
            ?: return hideArrow()
        val container = rootRef.current
            ?: return

        val documentPath = clientState.navigationRoute.documentPath
            ?: return hideArrow()
        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
            ?: return hideArrow()
        if (! ScriptConventions.isScript(documentNotation)) {
            return hideArrow()
        }

        val frame = clientState.clientLogicState.logicStatus?.active?.frame
        val nextToRun = LogicRunFrames.frameForDocument(frame, documentPath)?.position
            ?: return hideArrow()

        val rowElement = StepRowRefRegistry.get(nextToRun)
            ?: return hideArrow()  // row not registered yet — the registry observer re-fires when it mounts

        val containerRect = container.getBoundingClientRect()
        val rowRect = rowElement.getBoundingClientRect()
        val centerTop = rowRect.top - containerRect.top + rowRect.height / 2.0

        // Draggable while the run is SETTLED (paused at any boundary — a manual step settle, a Pause step,
        // or error-park) and this document is the run root — the same "!running && !stepping" gate the
        // server's moveTo enforces. isHaltPaused() would be too strict (it excludes a plain stepped Paused).
        val logicState = clientState.clientLogicState
        val canDrag = logicState.isActive() && ! logicState.isExecuting() &&
                frame?.objectLocation?.documentPath == documentPath

        updateArrow(centerTop, canDrag)
    }


    private fun hideArrow() {
        updateArrow(null, false)
    }


    private fun updateArrow(topPx: Double?, canDrag: Boolean) {
        if (state.arrowTopPx == topPx && state.draggable == canDrag) {
            return
        }
        setState {
            arrowTopPx = topPx
            draggable = canDrag
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onGlyphPointerDown(event: PointerEvent<HTMLDivElement>) {
        if (! state.draggable) {
            return
        }
        event.stopPropagation()
        event.preventDefault()

        val clientState = latestClientState
            ?: return
        val documentPath = clientState.navigationRoute.documentPath
            ?: return
        val frame = clientState.clientLogicState.logicStatus?.active?.frame
        val nextToRun = LogicRunFrames.frameForDocument(frame, documentPath)?.position
            ?: return

        val notation = clientState.graphStructure().graphNotation
        val tree = ScriptTree.read(documentPath, clientState.graphDefinitionAttempt.successful())

        val ordered = tree.orderedDescendantObjectPaths()
        val orderedIndex = ordered.withIndex().associate { (index, path) -> path to index }
        val nextIndex = orderedIndex[nextToRun.objectPath] ?: -1

        val hitRows = ordered
            .map { ObjectLocation(documentPath, it) }
            .filter { StepRowRefRegistry.get(it) != null }

        val validTargets = hitRows
            .filter { ScriptJumpAnalysis.isValidTarget(notation, documentPath, tree, it.objectPath) }
            .toSet()

        // Trace-free advisory (decision 2): warn for a FORWARD jump that would skip a producer a step
        // running at/after the target still depends on — the source is a path-predecessor of the target
        // (so it's skipped) that also sits after the current position (a genuine forward skip, not an
        // already-run earlier step), and the consumer is at/after the target (drop set → re-runs). Purely
        // advisory (never disables); the real backstop is the runtime "No value produced" error-park.
        val edges = ScriptDependencyAnalysis
            .analyze(clientState.graphDefinitionAttempt, documentPath)
            .edges
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

        dragNextToRun = nextToRun
        dragHitRows = hitRows
        dragValidTargets = validTargets
        dragWarnTargets = warnTargets

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
        if (! state.dragging) {
            return
        }
        val container = rootRef.current
            ?: return

        val candidateLocation = nearestStepRow(event.clientY)
        if (candidateLocation == null) {
            clearCandidate()
            return
        }

        val rowElement = StepRowRefRegistry.get(candidateLocation)
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

        dragNextToRun = null
        dragHitRows = listOf()
        dragValidTargets = setOf()
        dragWarnTargets = setOf()

        setState {
            dragging = false
            candidate = null
            candidateValid = false
            candidateWarn = false
            candidateTopPx = null
            candidateHeightPx = null
        }

        if (target != null && valid && target != nextToRun) {
            props.clientLogicGlobal.moveToAsync(target)
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


    // Nearest registered step row to the cursor Y: the row containing clientY if any, else the one whose
    // vertical center is closest. Adapted from ScriptBranchDisplay.computeInsertionFromCursor (nearest-row,
    // not insertion-index).
    private fun nearestStepRow(clientY: Double): ObjectLocation? {
        var best: ObjectLocation? = null
        var bestDistance = Double.MAX_VALUE
        for (location in dragHitRows) {
            val element = StepRowRefRegistry.get(location)
                ?: continue
            val rect = element.getBoundingClientRect()
            if (clientY >= rect.top && clientY <= rect.bottom) {
                return location
            }
            val center = rect.top + rect.height / 2.0
            val distance = abs(clientY - center)
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
                // The overlay itself is transparent to the mouse; only the glyph (and, while dragging, the
                // drop surface) opt back in, so cards below stay clickable.
                pointerEvents = None.none
            }

            renderGlyph()

            if (state.dragging) {
                renderCandidateHighlight()
                renderDragSurface()
            }
        }
    }


    private fun ChildrenBuilder.renderGlyph() {
        val topPx = state.arrowTopPx
            ?: return

        div {
            css {
                position = Position.absolute
                top = (topPx - glyphSizePx / 2.0).px
                // Hug the step cards' left edge, painted over the leftmost (dependency-lane) column — the
                // user-chosen "gutter-column strip" placement (overflow-safe, no scroll-pane clipping).
                left = 0.px
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
                    // next-to-run border, which also shows during a run.
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
            ! state.candidateValid -> invalidColour
            state.candidateWarn -> warnColour
            else -> validColour
        }

        div {
            css {
                position = Position.absolute
                top = topPx.px
                left = 0.px
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
