package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import react.ChildrenBuilder
import react.Key
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.AttributeDefinition
import tech.kzen.lib.common.model.definition.ListAttributeDefinition
import tech.kzen.lib.common.model.definition.MapAttributeDefinition
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.definition.ReferenceAttributeDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
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


private data class CrossBranchEdge(
    val source: ObjectLocation,
    val target: ObjectLocation
)


private val branchAttributeNames = listOf(
    ScriptConventions.stepsAttributeName,
    AttributeName("then"),
    AttributeName("else"))


//---------------------------------------------------------------------------------------------------------------------
class ScriptDependencyOverlay(
    props: ScriptDependencyOverlayProps
):
    RComponent<ScriptDependencyOverlayProps, ScriptDependencyOverlayState>(props),
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
    private fun computeCrossBranchEdges(clientState: ClientState): List<CrossBranchEdge> {
        val documentPath = clientState.navigationRoute.documentPath
            ?: return emptyList()
        val graphDefinitionAttempt = clientState.graphDefinitionAttempt
        val graphStructure = graphDefinitionAttempt.graphStructure
        val documentNotation = graphStructure.graphNotation.documents[documentPath]
            ?: return emptyList()
        if (! ScriptConventions.isScript(documentNotation)) {
            return emptyList()
        }

        val coalesce = graphStructure.graphNotation.coalesce
        val mainObjectLocation = documentPath.toMainObjectLocation()

        // Walk all branches (root + nested) and assign each step to its branch's AttributeLocation.
        val branchOfStep = mutableMapOf<ObjectLocation, AttributeLocation>()

        fun walkBranch(branchAttrLocation: AttributeLocation) {
            // NB: use the nullable firstAttribute(objectLocation, attributePath) form, not
            //     ScriptController.stepLocations which delegates through the throwing 1-arg form
            //     and would crash when probing attribute names a step doesn't have (e.g. Run.steps).
            val listNotation = graphStructure.graphNotation.firstAttribute(
                branchAttrLocation.objectLocation,
                branchAttrLocation.attributePath
            ) as? ListAttributeNotation
                ?: return
            val host = ObjectReferenceHost.ofLocation(branchAttrLocation.objectLocation)
            val steps = listNotation.values.mapNotNull { value ->
                val asString = value.asString()
                    ?: return@mapNotNull null
                val ref = try {
                    ObjectReference.parse(asString)
                } catch (_: Throwable) {
                    return@mapNotNull null
                }
                coalesce.locateOptional(ref, host)
            }
            for (step in steps) {
                branchOfStep[step] = branchAttrLocation
            }
            for (step in steps) {
                for (nestedName in branchAttributeNames) {
                    val nestedAttrLocation = AttributeLocation(step, AttributePath.ofName(nestedName))
                    walkBranch(nestedAttrLocation)
                }
            }
        }

        walkBranch(AttributeLocation(mainObjectLocation, ScriptConventions.stepsAttributePath))

        if (branchOfStep.isEmpty()) {
            return emptyList()
        }

        val locationByIdentifier = coalesce.map.keys
            .asSequence()
            .filter { it.documentPath == documentPath }
            .mapNotNull { location ->
                val name = location.objectPath.name.value
                if (validIdentifierRegex.matches(name)) name to location else null
            }
            .toMap()

        val edges = mutableSetOf<CrossBranchEdge>()

        fun classifyEdge(sourceLoc: ObjectLocation, targetLoc: ObjectLocation) {
            if (sourceLoc == targetLoc || sourceLoc.documentPath != documentPath) {
                return
            }
            if (sourceLoc.objectPath.startsWith(targetLoc.objectPath) ||
                targetLoc.objectPath.startsWith(sourceLoc.objectPath)) {
                return
            }
            val sourceBranch = branchOfStep[sourceLoc]
                ?: return
            val targetBranch = branchOfStep[targetLoc]
                ?: return
            if (sourceBranch != targetBranch) {
                edges.add(CrossBranchEdge(sourceLoc, targetLoc))
            }
        }

        val documentLocations = coalesce.map.keys.filter { it.documentPath == documentPath }
        for (targetLoc in documentLocations) {
            val objectDefinition = graphDefinitionAttempt.objectDefinitions[targetLoc]
                ?: continue
            val host = ObjectReferenceHost.ofLocation(targetLoc)

            for ((_, definitionReference) in objectDefinition.attributeReferencesIncludingWeak()) {
                val resolved = coalesce.locateOptional(definitionReference.objectReference, host)
                    ?: continue
                classifyEdge(resolved, targetLoc)
            }

            val objectNotation = coalesce[targetLoc]
                ?: continue
            walkValueScalars(objectDefinition, objectNotation) { stringValue ->
                for ((identifier, sourceLoc) in locationByIdentifier) {
                    if (sourceLoc == targetLoc) {
                        continue
                    }
                    if (containsWord(stringValue, identifier)) {
                        classifyEdge(sourceLoc, targetLoc)
                    }
                }
            }
        }

        return edges.toList()
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


//---------------------------------------------------------------------------------------------------------------------
private val validIdentifierRegex = Regex("^[A-Za-z_][A-Za-z0-9_]*$")


private fun containsWord(haystack: String, needle: String): Boolean {
    return Regex("\\b" + Regex.escape(needle) + "\\b").containsMatchIn(haystack)
}


private fun walkValueScalars(
    objectDefinition: ObjectDefinition,
    objectNotation: ObjectNotation,
    action: (String) -> Unit
) {
    for ((name, attributeDefinition) in objectDefinition.attributeDefinitions.map) {
        val attributeNotation = objectNotation.attributes.map[name]
            ?: continue
        walkValueScalar(attributeDefinition, attributeNotation, action)
    }
}


private fun walkValueScalar(
    attributeDefinition: AttributeDefinition,
    attributeNotation: AttributeNotation,
    action: (String) -> Unit
) {
    when (attributeDefinition) {
        is ReferenceAttributeDefinition ->
            return

        is ValueAttributeDefinition -> {
            if (attributeNotation is ScalarAttributeNotation) {
                action(attributeNotation.value)
            }
        }

        is ListAttributeDefinition -> {
            if (attributeNotation is ListAttributeNotation) {
                val children = attributeDefinition.values
                attributeNotation.values.forEachIndexed { i, childNotation ->
                    val childDef = children.getOrNull(i)
                        ?: return@forEachIndexed
                    walkValueScalar(childDef, childNotation, action)
                }
            }
        }

        is MapAttributeDefinition -> {
            if (attributeNotation is MapAttributeNotation) {
                for ((segment, childNotation) in attributeNotation.map) {
                    val childDef = attributeDefinition.map[segment.asKey()]
                        ?: continue
                    walkValueScalar(childDef, childNotation, action)
                }
            }
        }
    }
}
