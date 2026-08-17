package tech.kzen.auto.common.paradigm.flow

import tech.kzen.auto.common.paradigm.flow.FlowStructureTestBuilder.edge
import tech.kzen.auto.common.paradigm.flow.FlowStructureTestBuilder.matrixOf
import tech.kzen.auto.common.paradigm.flow.FlowStructureTestBuilder.vertex
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.EdgeOrientation
import tech.kzen.lib.common.model.attribute.AttributeName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


/**
 * Pins the grid-cell geometry of [tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix]:
 * the colspan window (a vertex occupies one column per declared input) and the per-input backward
 * trace through hand-placed pipe glyphs. This is the wiring model — a vertex's input i is fed by
 * whatever the cell at (row - 1, column + i) traces back to.
 */
class FlowMatrixTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun multiInputVertexOccupiesOneColumnPerInput() {
        val fanIn = vertex("fan-in", 1, 0, "a", "b")
        val matrix = matrixOf(fanIn)

        assertEquals(fanIn, matrix.get(1, 0))
        assertEquals(fanIn, matrix.get(1, 1))
        assertNull(matrix.get(1, 2))
        assertNull(matrix.get(0, 0))
    }


    @Test
    fun inputsTraceBackOneColumnPerInputOffset() {
        val left = vertex("left", 0, 0)
        val right = vertex("right", 0, 1)
        val fanIn = vertex("fan-in", 1, 0, "a", "b")
        val matrix = matrixOf(left, right, fanIn)

        assertEquals(left, matrix.traceVertexBackFrom(fanIn, AttributeName("a")))
        assertEquals(right, matrix.traceVertexBackFrom(fanIn, AttributeName("b")))
        assertEquals(setOf(left, right), matrix.traceVertexBackFrom(fanIn.objectLocation))
    }


    @Test
    fun unwiredInputTracesToNull() {
        val above = vertex("above", 0, 1)
        val fanIn = vertex("fan-in", 1, 0, "a", "b")
        val matrix = matrixOf(above, fanIn)

        assertNull(matrix.traceVertexBackFrom(fanIn, AttributeName("a")))
        assertEquals(above, matrix.traceVertexBackFrom(fanIn, AttributeName("b")))
        assertEquals(setOf(above), matrix.traceVertexBackFrom(fanIn.objectLocation))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun rightToLeftPipeRunTracesToSource() {
        val source = vertex("source", 0, 2)
        val sink = vertex("sink", 2, 0, "input")
        val matrix = matrixOf(
                source,
                edge(EdgeOrientation.TopToLeft, 1, 2),
                edge(EdgeOrientation.RightToLeft, 1, 1),
                edge(EdgeOrientation.RightToBottom, 1, 0),
                sink)

        assertEquals(source, matrix.traceVertexBackFrom(sink, AttributeName("input")))
    }


    @Test
    fun leftToRightPipeRunTracesToSource() {
        val source = vertex("source", 0, 0)
        val sink = vertex("sink", 2, 2, "input")
        val matrix = matrixOf(
                source,
                edge(EdgeOrientation.TopToRight, 1, 0),
                edge(EdgeOrientation.LeftToRight, 1, 1),
                edge(EdgeOrientation.LeftToBottom, 1, 2),
                sink)

        assertEquals(source, matrix.traceVertexBackFrom(sink, AttributeName("input")))
    }


    @Test
    fun fanOutPipeTracesBothSinksToSameSource() {
        val source = vertex("source", 0, 1)
        val sinkLeft = vertex("sink-left", 2, 0, "input")
        val sinkRight = vertex("sink-right", 2, 2, "input")
        val matrix = matrixOf(
                source,
                edge(EdgeOrientation.RightToBottom, 1, 0),
                edge(EdgeOrientation.TopToLeftAndRight, 1, 1),
                edge(EdgeOrientation.LeftToBottom, 1, 2),
                sinkLeft,
                sinkRight)

        assertEquals(source, matrix.traceVertexBackFrom(sinkLeft, AttributeName("input")))
        assertEquals(source, matrix.traceVertexBackFrom(sinkRight, AttributeName("input")))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun edgeTraceCollectsEveryPipeCellBackToTheSource() {
        val source = vertex("source", 0, 0)
        val sink = vertex("sink", 2, 2, "input")
        val corner = edge(EdgeOrientation.TopToRight, 1, 0)
        val run = edge(EdgeOrientation.LeftToRight, 1, 1)
        val drop = edge(EdgeOrientation.LeftToBottom, 1, 2)
        val matrix = matrixOf(source, corner, run, drop, sink)

        // Nearest-first: the cell above the input, then the run leftward, stopping at the vertex.
        assertEquals(listOf(drop, run, corner), matrix.traceEdgeBackFrom(sink, 0))
    }


    @Test
    fun edgeTraceStopsWhereTheRunIsSevered() {
        val source = vertex("source", 0, 0)
        val sink = vertex("sink", 2, 2, "input")
        val corner = edge(EdgeOrientation.TopToRight, 1, 0)
        // Fed from the right rather than the left, so nothing connects it to the corner beside it.
        val severed = edge(EdgeOrientation.RightToBottom, 1, 1)
        val drop = edge(EdgeOrientation.LeftToBottom, 1, 2)
        val matrix = matrixOf(source, corner, severed, drop, sink)

        assertEquals(listOf(drop), matrix.traceEdgeBackFrom(sink, 0))
        assertNull(matrix.traceVertexBackFrom(sink, AttributeName("input")))
    }


    @Test
    fun edgeTraceIsEmptyWhenNothingDropsIntoTheInput() {
        val sink = vertex("sink", 2, 0, "input")
        val matrix = matrixOf(edge(EdgeOrientation.TopToLeft, 1, 0), sink)

        assertEquals(listOf(), matrix.traceEdgeBackFrom(sink, 0))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun brokenPipeRunTracesToNull() {
        // The horizontal run is severed: (1, 1) carries no left egress, so the sink's input
        // reaches nothing even though a pipe glyph sits directly above it.
        val source = vertex("source", 0, 2)
        val sink = vertex("sink", 2, 0, "input")
        val matrix = matrixOf(
                source,
                edge(EdgeOrientation.TopToLeft, 1, 2),
                edge(EdgeOrientation.TopToBottom, 1, 1),
                edge(EdgeOrientation.RightToBottom, 1, 0),
                sink)

        assertNull(matrix.traceVertexBackFrom(sink, AttributeName("input")))
    }
}
