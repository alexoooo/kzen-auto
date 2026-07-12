package tech.kzen.auto.common.paradigm.flow

import tech.kzen.auto.common.paradigm.flow.FlowStructureTestBuilder.edge
import tech.kzen.auto.common.paradigm.flow.FlowStructureTestBuilder.matrixOf
import tech.kzen.auto.common.paradigm.flow.FlowStructureTestBuilder.vertex
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowDag
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.EdgeOrientation
import tech.kzen.lib.common.model.attribute.AttributeName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


/**
 * Pins the geometry→DAG derivation of [FlowDag.of]: successor discovery below each vertex
 * (including the leftward multi-cell scan onto a multi-input vertex's colspan), predecessor
 * inversion, and the layer computation.
 *
 * Note there is no cycle case: every successor hop moves laterally within a pipe row or strictly
 * downward, so a matrix-derived DAG cannot contain a cycle — the `check` in the layer computation
 * is purely defensive.
 */
class FlowDagTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun straightChainYieldsOneVertexPerLayer() {
        val source = vertex("source", 0, 0)
        val sink = vertex("sink", 2, 0, "input")
        val dag = FlowDag.of(matrixOf(
                source,
                edge(EdgeOrientation.TopToBottom, 1, 0),
                sink))

        assertEquals(listOf(sink.objectLocation), dag.successors[source.objectLocation])
        assertEquals(listOf(), dag.successors[sink.objectLocation])
        assertEquals(listOf(source.objectLocation), dag.predecessors[sink.objectLocation])
        assertEquals(
                listOf(listOf(source.objectLocation), listOf(sink.objectLocation)),
                dag.layers)
    }


    @Test
    fun adjacentVertexIsDirectSuccessor() {
        val source = vertex("source", 0, 0)
        val sink = vertex("sink", 1, 0, "input")
        val dag = FlowDag.of(matrixOf(source, sink))

        assertEquals(listOf(sink.objectLocation), dag.successors[source.objectLocation])
        assertEquals(
                listOf(listOf(source.objectLocation), listOf(sink.objectLocation)),
                dag.layers)
    }


    @Test
    fun fanInBothSourcesFeedMultiInputVertex() {
        val left = vertex("left", 0, 0)
        val right = vertex("right", 0, 1)
        val fanIn = vertex("fan-in", 1, 0, "a", "b")
        val dag = FlowDag.of(matrixOf(left, right, fanIn))

        assertEquals(listOf(fanIn.objectLocation), dag.successors[left.objectLocation])
        assertEquals(listOf(fanIn.objectLocation), dag.successors[right.objectLocation])
        assertEquals(
                listOf(left.objectLocation, right.objectLocation),
                dag.predecessors[fanIn.objectLocation])
        assertEquals(
                listOf(
                    listOf(left.objectLocation, right.objectLocation),
                    listOf(fanIn.objectLocation)),
                dag.layers)
    }


    @Test
    fun fanOutDuplicatesToAllSinksBelow() {
        val source = vertex("source", 0, 1)
        val sinkLeft = vertex("sink-left", 2, 0, "input")
        val sinkMiddle = vertex("sink-middle", 2, 1, "input")
        val sinkRight = vertex("sink-right", 2, 2, "input")
        val dag = FlowDag.of(matrixOf(
                source,
                edge(EdgeOrientation.RightToBottom, 1, 0),
                edge(EdgeOrientation.TopToBottomAndLeftAndRight, 1, 1),
                edge(EdgeOrientation.LeftToBottom, 1, 2),
                sinkLeft,
                sinkMiddle,
                sinkRight))

        // Trace order: straight down first, then the right egress, then the left egress.
        assertEquals(
                listOf(sinkMiddle.objectLocation, sinkRight.objectLocation, sinkLeft.objectLocation),
                dag.successors[source.objectLocation])
        assertEquals(
                listOf(
                    listOf(source.objectLocation),
                    listOf(sinkLeft.objectLocation, sinkMiddle.objectLocation, sinkRight.objectLocation)),
                dag.layers)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun multiInputVertexReachedFromColumnItsSpanCovers() {
        val source = vertex("source", 0, 2)
        val fanIn = vertex("fan-in", 1, 1, "a", "b")
        val dag = FlowDag.of(matrixOf(source, fanIn))

        assertEquals(listOf(fanIn.objectLocation), dag.successors[source.objectLocation])

        // And the wiring is symmetric: input "b" (column 2) traces back to the source.
        val matrix = matrixOf(source, fanIn)
        assertEquals(source, matrix.traceVertexBackFrom(fanIn, AttributeName("b")))
    }


    @Test
    fun leftwardScanReachesVertexBeyondItsSpan() {
        // Pins today's findCellBelow scan arithmetic: the source at column 3 finds the fan-in
        // (span columns 1-2) as a successor via the leftward scan, even though NO input of the
        // fan-in traces back to it — a forward/backward asymmetry. Such a vertex never becomes
        // ready (its inputs are unwired), which the structure lint reports.
        val source = vertex("source", 0, 3)
        val fanIn = vertex("fan-in", 1, 1, "a", "b")
        val matrix = matrixOf(source, fanIn)
        val dag = FlowDag.of(matrix)

        assertEquals(listOf(fanIn.objectLocation), dag.successors[source.objectLocation])
        assertNull(matrix.traceVertexBackFrom(fanIn, AttributeName("a")))
        assertNull(matrix.traceVertexBackFrom(fanIn, AttributeName("b")))
    }


    @Test
    fun leftwardScanStopsAtVertexWhoseSpanEndsShort() {
        val source = vertex("source", 0, 3)
        val narrow = vertex("narrow", 1, 1, "input")
        val dag = FlowDag.of(matrixOf(source, narrow))

        assertEquals(listOf(), dag.successors[source.objectLocation])
        assertEquals(listOf(listOf(source.objectLocation, narrow.objectLocation)), dag.layers)
    }
}
