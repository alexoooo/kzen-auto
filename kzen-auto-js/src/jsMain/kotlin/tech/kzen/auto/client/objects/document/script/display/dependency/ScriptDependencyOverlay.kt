package tech.kzen.auto.client.objects.document.script.display.dependency

import emotion.react.css
import react.ChildrenBuilder
import react.Key
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptDependencyAnalysis
import tech.kzen.auto.common.objects.document.script.model.ScriptStepDependency
import web.animations.requestAnimationFrame
import web.cssom.None
import web.cssom.Position
import web.cssom.px
import web.html.HTMLDivElement
import web.resize.ResizeObserver


//---------------------------------------------------------------------------------------------------------------------
external interface ScriptDependencyOverlayProps: Props


external interface ScriptDependencyOverlayState: State {
    var segments: List<OverlaySegment>
}


//---------------------------------------------------------------------------------------------------------------------
data class OverlaySegment(
    val key: String,
    val leftPx: Double,
    val topPx: Double,
    val widthPx: Double,
    val heightPx: Double
)


//---------------------------------------------------------------------------------------------------------------------
class ScriptDependencyOverlay(
    props: ScriptDependencyOverlayProps
):
    RPureComponent<ScriptDependencyOverlayProps, ScriptDependencyOverlayState>(props),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    private val rootRef = createRef<HTMLDivElement>()
    private var unsubscribeRegistry: (() -> Unit)? = null
    private var resizeObserver: ResizeObserver? = null
    private var observedElement: HTMLDivElement? = null
    private var latestClientState: ClientState? = null
    private var rafScheduled: Boolean = false


    //-----------------------------------------------------------------------------------------------------------------
    override fun ScriptDependencyOverlayState.init(props: ScriptDependencyOverlayProps) {
        segments = emptyList()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.clientStateGlobal.observe(this)
        unsubscribeRegistry = StepRowRefRegistry.observe { scheduleRemeasure() }
        attachResizeObserver()
    }


    override fun componentDidUpdate(
        prevProps: ScriptDependencyOverlayProps,
        prevState: ScriptDependencyOverlayState,
        snapshot: Any
    ) {
        // NB: rootRef may attach a new HTMLDivElement after re-render; rebind ResizeObserver if so.
        attachResizeObserver()
    }


    override fun componentWillUnmount() {
        ClientContext.clientStateGlobal.unobserve(this)
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
        // NB: defer measurement to the next animation frame so the browser has finalized any
        //     in-flight layout. The mount-cascade of step rows (especially inside IfStep's table
        //     subtree) can otherwise yield correct-at-this-instant rects that subsequent layout
        //     work invalidates — visible as a missing cross-branch polyline until the user
        //     expand-then-collapses a row to force a ResizeObserver pass.
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
        val clientState = latestClientState
            ?: return
        val container = rootRef.current
            ?: return

        val edges = computeCrossBranchEdges(clientState)
        if (edges.isEmpty()) {
            updateSegments(emptyList())
            return
        }

        val containerRect = container.getBoundingClientRect()
        val halfLane = stepDependencyLaneWidthPx.toDouble() / 2.0
        val halfTrunk = stepDependencyTrunkLineWidthPx.toDouble() / 2.0
        val halfMarker = stepDependencyMarkerSizePx.toDouble() / 2.0

        val newSegments = mutableListOf<OverlaySegment>()
        for (edge in edges) {
            val sourceElement = StepRowRefRegistry.get(edge.source)
                ?: continue
            val targetElement = StepRowRefRegistry.get(edge.target)
                ?: continue

            val sourceRect = sourceElement.getBoundingClientRect()
            val targetRect = targetElement.getBoundingClientRect()

            // NB: phantom-lane (leftmost gutter cell) center sits at row.left + laneWidth/2.
            //     Source marker bottom-edge sits at row.bottom; target marker center sits at
            //     row.top + halfMarker. Routing the horizontal at the target marker's vertical
            //     center makes the line enter the filled circle from its side, mirroring how
            //     the vertical in-branch trunks emerge from each marker's center.
            val sx = sourceRect.left - containerRect.left + halfLane
            val sy = sourceRect.bottom - containerRect.top
            val tx = targetRect.left - containerRect.left + halfLane
            val ty = targetRect.top - containerRect.top + halfMarker

            // Vertical segment from source y down/up to target y, at source x.
            val vTop = minOf(sy, ty)
            val vBottom = maxOf(sy, ty)
            val edgeKey = edge.source.toReference().asString() + "->" + edge.target.toReference().asString()
            newSegments.add(OverlaySegment(
                key = "$edgeKey:v",
                leftPx = sx - halfTrunk,
                topPx = vTop,
                widthPx = stepDependencyTrunkLineWidthPx.toDouble(),
                heightPx = vBottom - vTop))

            // Horizontal segment at target y, from source x to target x.
            val hLeft = minOf(sx, tx)
            val hRight = maxOf(sx, tx)
            newSegments.add(OverlaySegment(
                key = "$edgeKey:h",
                leftPx = hLeft,
                topPx = ty - halfTrunk,
                widthPx = hRight - hLeft,
                heightPx = stepDependencyTrunkLineWidthPx.toDouble()))
        }

        updateSegments(newSegments)
    }


    private fun updateSegments(newSegments: List<OverlaySegment>) {
        if (state.segments == newSegments) {
            return
        }
        setState {
            segments = newSegments
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun computeCrossBranchEdges(clientState: ClientState): List<ScriptStepDependency> {
        val documentPath = clientState.navigationRoute.documentPath
            ?: return emptyList()
        val graphStructure = clientState.graphDefinitionAttempt.graphStructure
        val documentNotation = graphStructure.graphNotation.documents[documentPath]
            ?: return emptyList()
        if (!ScriptConventions.isScript(documentNotation)) {
            return emptyList()
        }

        return ScriptDependencyAnalysis
            .analyze(clientState.graphDefinitionAttempt, documentPath)
            .crossBranchEdges()
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
                pointerEvents = None.none
            }

            for (segment in state.segments) {
                div {
                    key = Key(segment.key)
                    css {
                        position = Position.absolute
                        left = segment.leftPx.px
                        top = segment.topPx.px
                        width = segment.widthPx.px
                        height = segment.heightPx.px
                        backgroundColor = stepDependencyTrunkColor
                    }
                }
            }
        }
    }
}
