package tech.kzen.auto.client.objects.document.graph
//
//import kotlinx.css.*
//import kotlinx.css.properties.borderBottom
//import kotlinx.css.properties.borderLeft
//import kotlinx.css.properties.borderRight
//import kotlinx.css.properties.borderTop
//import kotlinx.html.js.onMouseOutFunction
//import kotlinx.html.js.onMouseOverFunction
//import react.*
//import react.dom.attrs
//import styled.css
//import styled.styledDiv
//import tech.kzen.auto.client.objects.document.graph.edge.BottomEgress
//import tech.kzen.auto.client.objects.document.graph.edge.TopIngress
//import tech.kzen.auto.client.service.ClientContext
//import tech.kzen.auto.client.util.async
//import tech.kzen.auto.client.wrap.material.DeleteIcon
//import tech.kzen.auto.client.wrap.material.MaterialIconButton
//import tech.kzen.auto.client.wrap.reactStyle
//import tech.kzen.auto.common.objects.document.graph.GraphDocument
//import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
//import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowDag
//import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowMatrix
//import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.CellCoordinate
//import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.EdgeDescriptor
//import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.EdgeDirection
//import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.VertexDescriptor
//import tech.kzen.auto.common.paradigm.dataflow.util.DataflowUtils
//import tech.kzen.lib.common.model.attribute.AttributeNesting
//import tech.kzen.lib.common.model.attribute.AttributePath
//import tech.kzen.lib.common.model.document.DocumentPath
//import tech.kzen.lib.common.model.locate.ObjectLocation
//import tech.kzen.lib.common.model.structure.GraphStructure
//import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveInAttributeCommand
//import tech.kzen.lib.common.service.notation.NotationConventions
//
//
////---------------------------------------------------------------------------------------------------------------------
//external interface EdgeControllerProps: Props {
//    var cellDescriptor: EdgeDescriptor
//
//    var documentPath: DocumentPath
//    var attributeNesting: AttributeNesting
//    var graphStructure: GraphStructure
//    var visualDataflowModel: VisualDataflowModel
//    var dataflowMatrix: DataflowMatrix
//    var dataflowDag: DataflowDag
//}
//
//
//external interface EdgeControllerState: State {
//    var edgeHover: Boolean
//}
//
//
////---------------------------------------------------------------------------------------------------------------------
//class EdgeController(
//        props: EdgeControllerProps
//):
//        RPureComponent<EdgeControllerProps, EdgeControllerState>(props)
//{
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun EdgeControllerState.init(props: EdgeControllerProps) {
//        edgeHover = false
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun onMouseOver() {
//        setState {
//            edgeHover = true
//        }
//    }
//
//
//    private fun onMouseOut() {
//        setState {
//            edgeHover = false
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun onRemove() {
//        async {
//            val sourceMain = ObjectLocation(
//                    props.documentPath,
//                    NotationConventions.mainObjectPath)
//
//            val objectAttributePath = AttributePath(
//                    GraphDocument.edgesAttributeName,
//                    props.attributeNesting)
//
//            ClientContext.mirroredGraphStore.apply(RemoveInAttributeCommand(
//                    sourceMain, objectAttributePath, false))
//        }
//    }
//
//
//    private fun nextToRun(): ObjectLocation? {
//        return props.visualDataflowModel.running()
//                ?: DataflowUtils.next(
//                        props.documentPath,
//                        props.graphStructure,
//                        props.visualDataflowModel)
//    }
//
//
//    private fun pendingToRunVertexDescriptor(
//            nextToRun: ObjectLocation
//    ): VertexDescriptor? {
//        val nextToRunVisualVertexModel = props.visualDataflowModel.vertices[nextToRun]
//                ?: return null
//
//        if (nextToRunVisualVertexModel.epoch > 0) {
//            return null
//        }
//
//        // NB: might be null when navigating to new document while running
//        return props.dataflowMatrix.verticesByLocation[nextToRun]
//    }
//
//
//    private fun edgesFlowingToPending(
//            nextToRun: VertexDescriptor
//    ): Set<EdgeDescriptor> {
//        val builder = mutableSetOf<EdgeDescriptor>()
//
//        for ((objectLocation, vertexVisualModel) in props.visualDataflowModel.vertices) {
//            if (vertexVisualModel.message == null) {
//                continue
//            }
//
//            val pendingSuccessors = pendingSuccessors(objectLocation)
//            if (nextToRun.objectLocation in pendingSuccessors) {
//                continue
//            }
//
//            for (pendingSuccessor in pendingSuccessors) {
//                val successorVertexDescriptor = props.dataflowMatrix.verticesByLocation[pendingSuccessor]
//                        ?: throw IllegalStateException()
//
//                val edgesToSuccessor = edgesLeadingTo(successorVertexDescriptor)
//
//                val successorsUpToNextToRun = edgesToSuccessor
//                        .filter { it.coordinate.row <= nextToRun.coordinate.row }
//
//                builder.addAll(successorsUpToNextToRun)
//            }
//        }
//
//        return builder
//    }
//
//
//    private fun pendingWithAvailableMessage(): Set<ObjectLocation> {
//        return props.visualDataflowModel.vertices
//                .filter { it.value.message != null }
//                .flatMap { pendingSuccessors(it.key) }
//                .toSet()
//    }
//
//
//    private fun edgesAvailableToPending(
//            pendingWithAvailableMessage: Collection<ObjectLocation>
//    ): Set<EdgeDescriptor> {
//        return pendingWithAvailableMessage
//                .mapNotNull { props.dataflowMatrix.verticesByLocation[it] }
//                .flatMap { edgesLeadingTo(it) }
//                .toSet()
//
////        val builder = mutableSetOf<EdgeDescriptor>()
////
////        for ((objectLocation, vertexVisualModel) in props.visualDataflowModel.vertices) {
////            if (vertexVisualModel.message == null) {
////                continue
////            }
////
////            val pendingSuccessors = pendingSuccessors(objectLocation)
////
////            for (pendingSuccessor in pendingSuccessors) {
////                val successorVertexDescriptor = props.dataflowMatrix.verticesByLocation[pendingSuccessor]
////                        ?: throw IllegalStateException()
////
////                val edgesToSuccessor = edgesLeadingTo(successorVertexDescriptor)
////
////                builder.addAll(edgesToSuccessor)
////            }
////        }
////
////        return builder
//    }
//
//
//    private fun pendingSuccessors(
//            objectLocation: ObjectLocation
//    ): List<ObjectLocation> {
//        val successors = props.dataflowDag.successors[objectLocation]
//                ?: return listOf()
//
//        val builder = mutableListOf<ObjectLocation>()
//
//        for (successor in successors) {
//            val successorVisualVertexModel =
//                    props.visualDataflowModel.vertices[successor]
//                    ?: continue
//
//            if (successorVisualVertexModel.epoch == 0) {
//                builder.add(successor)
//            }
//        }
//
//        return builder
//    }
//
//
//    private fun edgesLeadingTo(
//            nextToRun: VertexDescriptor
//    ): Set<EdgeDescriptor> {
//        val buffer = mutableSetOf<EdgeDescriptor>()
//        for ((i, inputName) in nextToRun.inputNames.withIndex()) {
//            val sourceVertex = props.dataflowMatrix.traceVertexBackFrom(nextToRun, inputName)
//                    ?: continue
//
//            val sourceVisualModel = props.visualDataflowModel.vertices[sourceVertex.objectLocation]
//                    ?: continue
//
//            if (sourceVisualModel.message == null) {
//                continue
//            }
//
//            val leadingEdges = props.dataflowMatrix.traceEdgeBackFrom(nextToRun, i)
//            buffer.addAll(leadingEdges)
//        }
//        return buffer
//    }
//
//
//    private fun isEgressActive(
//            edgeDirection: EdgeDirection,
//            nextCoordinate: CellCoordinate,
//            nextToRun: ObjectLocation,
//            edgesLeadingToActive: Set<EdgeDescriptor>
//    ): Boolean {
//        val activeEdgeAtOffset = edgesLeadingToActive.find { it.coordinate == nextCoordinate }
//        if (activeEdgeAtOffset != null) {
//            return activeEdgeAtOffset.orientation.hasIngress(edgeDirection.reverse())
//        }
//
//        val vertexDescriptor = props.dataflowMatrix.get(nextCoordinate) as? VertexDescriptor
//                ?: return false
//
//        return edgeDirection == EdgeDirection.Bottom &&
//                nextToRun == vertexDescriptor.objectLocation &&
//                props.visualDataflowModel.vertices[nextToRun]?.epoch == 0
//    }
//
//
//    private fun isEgressAvailable(
//            edgeDirection: EdgeDirection,
//            offsetCoordinate: CellCoordinate,
//            edgesLeadingToNextToRun: Set<EdgeDescriptor>,
//            pendingWithAvailableMessage: Set<ObjectLocation>
//    ): Boolean {
//        val nextToRunEdgeAtOffset = edgesLeadingToNextToRun.find { it.coordinate == offsetCoordinate }
//        if (nextToRunEdgeAtOffset != null) {
//            return nextToRunEdgeAtOffset.orientation.hasIngress(edgeDirection.reverse())
//        }
//
//        val vertexDescriptor = props.dataflowMatrix.get(offsetCoordinate) as? VertexDescriptor
//                ?: return false
//
//        return edgeDirection == EdgeDirection.Bottom &&
//                vertexDescriptor.objectLocation in pendingWithAvailableMessage
//    }
//
//
//    // TODO: refactor
//    private fun egressColor(
//            edgeDirection: EdgeDirection,
//            isRunning: Boolean,
//            nextToRun: ObjectLocation?,
//            hasMessage: Boolean,
//            edgesLeadingToNextToRun: Set<EdgeDescriptor>,
//            edgesInFlightToPending: Set<EdgeDescriptor>,
//            edgesAvailableToPending: Set<EdgeDescriptor>,
//            pendingWithAvailableMessage: Set<ObjectLocation>
//    ): Color {
//        if (nextToRun == null || ! hasMessage) {
//            return Color.white
//        }
//
//        val nextCoordinate = props.cellDescriptor.coordinate.offset(edgeDirection)
//
//        val isSending = isEgressActive(
//                edgeDirection, nextCoordinate, nextToRun, edgesLeadingToNextToRun)
//
//        val isInFlight = isEgressActive(
//                edgeDirection, nextCoordinate, nextToRun, edgesInFlightToPending)
//
//        val isEdgeMessageAvailable = isEgressAvailable(
//                edgeDirection, nextCoordinate, edgesAvailableToPending, pendingWithAvailableMessage)
//
//        return when {
//            isSending ->
//                if (isRunning) {
//                    Color.gold.lighten(25)
//                }
//                else {
//                    Color.gold
////                    Color.green
//                }
//
//            isInFlight ->
//                Color.gold.lighten(50)
//
//            isEdgeMessageAvailable ->
//                Color.gold.lighten(90)
//
//            else ->
//                Color.white
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun RBuilder.render() {
//        styledDiv {
//            css {
//                position = Position.relative
//                filter = "drop-shadow(0 1px 1px gray)"
//                width = CellController.cardWidth
//
//                height = 100.pct
//            }
//
//            attrs {
//                onMouseOverFunction = {
//                    onMouseOver()
//                }
//
//                onMouseOutFunction = {
//                    onMouseOut()
//                }
//            }
//
//            renderEdge()
//        }
//    }
//
//
//    private fun RBuilder.renderEdge() {
////        val isDebug =
////                props.cellDescriptor.coordinate.row == 5 &&
////                props.cellDescriptor.coordinate.column == 0
//
//        val orientation = props.cellDescriptor.orientation
//
//        val isRunning = props.visualDataflowModel.isRunning()
//        val nextToRun = nextToRun()
//        val pendingToRunVertexDescriptor = nextToRun?.let { pendingToRunVertexDescriptor(it) }
//
//        val edgesLeadingToNextToRun = pendingToRunVertexDescriptor
//                ?.let { edgesLeadingTo(it) }
//                ?: setOf()
//
//        val edgesInFlightToPending = pendingToRunVertexDescriptor
//                ?.let { edgesFlowingToPending(it) }
//                ?: setOf()
//
////        val edgesSendingMessages = edgesLeadingToNextToRun + edgesInFlightToPending
//
//        val isEdgeSendingMessage = props.cellDescriptor in edgesLeadingToNextToRun
//        val isEdgeInFlightMessage = props.cellDescriptor in edgesInFlightToPending
//
//        val pendingWithAvailableMessage = pendingWithAvailableMessage()
//        val edgesAvailableToPending = edgesAvailableToPending(pendingWithAvailableMessage)
//        val isEdgeMessageAvailable = props.cellDescriptor in edgesAvailableToPending
//
//        val hasMessage = isEdgeSendingMessage || isEdgeInFlightMessage || isEdgeMessageAvailable
//
//        val ingressAndCentreColor = when {
//            isEdgeSendingMessage ->
//                if (isRunning) {
//                    Color.gold.lighten(25)
//                }
//                else {
//                    Color.gold
//                }
//
//            isEdgeInFlightMessage ->
//                Color.gold.lighten(50)
//
//            isEdgeMessageAvailable ->
//                Color.gold.lighten(90)
//
//            else ->
//                Color.white
//        }
//
//        if (orientation.hasTop()) {
//            child(TopIngress::class) {
//                attrs {
//                    ingressColor = ingressAndCentreColor
//                }
//            }
//        }
//        else {
//            styledDiv {
//                css {
//                    height = CellController.ingressLength
//                }
//            }
//        }
//
//        styledDiv {
//            css {
//                width = CellController.cardWidth
//                marginBottom = (-5).px
//            }
//
//            when {
//                orientation.hasLeftIngress() ->
//                    renderIngressLeft(ingressAndCentreColor)
//
//                orientation.hasLeftEgress() -> {
//                    val edgeColor = egressColor(
//                            EdgeDirection.Left,
//                            isRunning,
//                            nextToRun,
//                            hasMessage,
//                            edgesLeadingToNextToRun,
//                            edgesInFlightToPending,
//                            edgesAvailableToPending,
//                            pendingWithAvailableMessage)
//                    renderEgressLeft(edgeColor)
//                }
//
//                else -> styledDiv {
//                    css {
//                        display = Display.inlineBlock
//                        width = CellController.cardWidth.div(2).minus(CellController.cardHorizontalMargin)
//                        height = CellController.arrowSide
//                    }
//                }
//            }
//
//            styledDiv {
//                css {
//                    display = Display.inlineBlock
//                    width = CellController.arrowSide
//                    height = CellController.arrowSide
//
//                    backgroundColor = ingressAndCentreColor
//                }
//
//                child(MaterialIconButton::class) {
//                    attrs {
//                        title = "Remove"
//
//                        style = reactStyle {
//                            marginTop = (-0.25).em
//                            marginRight = (-8).px
//                            float = Float.right
//
//                            if (! state.edgeHover) {
//                                visibility = Visibility.hidden
//                            }
//                        }
//
//                        onClick = ::onRemove
//                    }
//
//                    child(DeleteIcon::class) {}
//                }
//            }
//
//            if (orientation.hasRightEgress()) {
//                val edgeColor = egressColor(
//                        EdgeDirection.Right,
//                        isRunning,
//                        nextToRun,
//                        hasMessage,
//                        edgesLeadingToNextToRun,
//                        edgesInFlightToPending,
//                        edgesAvailableToPending,
//                        pendingWithAvailableMessage)
//                renderEgressRight(edgeColor)
//            }
//            else if (orientation.hasRightIngress()) {
//                renderIngressRight(ingressAndCentreColor)
//            }
//        }
//
//        if (orientation.hasBottom()) {
//            val edgeColor = egressColor(
//                    EdgeDirection.Bottom,
//                    isRunning,
//                    nextToRun,
//                    hasMessage,
//                    edgesLeadingToNextToRun,
//                    edgesInFlightToPending,
//                    edgesAvailableToPending,
//                    pendingWithAvailableMessage)
//            child(BottomEgress::class) {
//                attrs {
//                    egressColor = edgeColor
//                }
//            }
//        }
//        else {
//            styledDiv {
//                css {
//                    height = CellController.egressLength
//                }
//            }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun RBuilder.renderEgressLeft(
//            cardColor: Color
//    ) {
//        styledDiv {
//            css {
//                display = Display.inlineBlock
//                marginTop = CellController.cardHorizontalMargin.unaryMinus()
//            }
//
//            styledDiv {
//                css {
//                    width = 0.px
//                    height = 0.px
//
//                    borderRight(CellController.arrowSide, BorderStyle.solid, cardColor)
//                    borderTop(CellController.arrowSide, BorderStyle.solid, Color.transparent)
//                    borderBottom(CellController.arrowSide, BorderStyle.solid, Color.transparent)
//                }
//            }
//
//            styledDiv {
//                css {
//                    backgroundColor = cardColor
//
//                    width = CellController.cardWidth.div(2)
//                            .minus(CellController.cardHorizontalMargin)
//                            .minus(CellController.arrowSide)
//                    height = CellController.arrowSide
//                    marginTop = (-3).em
//                    marginLeft = CellController.arrowSide
//                }
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderEgressRight(
//            cardColor: Color
//    ) {
//        styledDiv {
//            css {
//                display = Display.inlineBlock
//                backgroundColor = cardColor
//
//                width = CellController.cardWidth.div(2).minus(CellController.egressLength)
//
//                height = 2.em
//            }
//        }
//
//        styledDiv {
//            css {
//                display = Display.inlineBlock
//
//                width = 0.px
//                height = 0.px
//
//                borderLeft(2.em, BorderStyle.solid, cardColor)
//                borderTop(2.em, BorderStyle.solid, Color.transparent)
//                borderBottom(2.em, BorderStyle.solid, Color.transparent)
//
//                marginTop = (-1).em
//                marginBottom = (-1).em
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderIngressLeft(
//            cardColor: Color
//    ) {
//        styledDiv {
//            css {
//                display = Display.inlineBlock
//                marginTop = CellController.cardHorizontalMargin.unaryMinus()
//            }
//
//            styledDiv {
//                css {
//                    width = 0.px
//                    height = 0.px
//
//                    borderLeft(CellController.arrowSide, BorderStyle.solid, cardColor)
//                    borderTop(CellController.arrowSide, BorderStyle.solid, Color.transparent)
//                    borderBottom(CellController.arrowSide, BorderStyle.solid, Color.transparent)
//                }
//            }
//
//            styledDiv {
//                css {
//                    backgroundColor = cardColor
//
//                    width = CellController.cardWidth.div(2).minus(CellController.cardHorizontalMargin)
//                    height = CellController.arrowSide
//                    marginTop = (-3).em
//                }
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderIngressRight(
//            cardColor: Color
//    ) {
//        styledDiv {
//            css {
//                display = Display.inlineBlock
//                backgroundColor = cardColor
////                backgroundColor = Color.burlyWood
//
//                width = CellController.cardWidth.div(2)
//                        .minus(CellController.arrowSide).plus(3.px)
//                //.minus(CellController.egressLength)
//
//                height = 2.em
//            }
//        }
//
//        styledDiv {
//            css {
//                display = Display.inlineBlock
//
//                width = 0.px
//                height = 0.px
//
//                borderRight(2.em, BorderStyle.solid, cardColor)
//                borderTop(2.em, BorderStyle.solid, Color.transparent)
//                borderBottom(2.em, BorderStyle.solid, Color.transparent)
//
//                marginTop = (-3).em.minus(3.px)
//                marginBottom = (-1).em
//                float = Float.right
//            }
//        }
//    }
//}