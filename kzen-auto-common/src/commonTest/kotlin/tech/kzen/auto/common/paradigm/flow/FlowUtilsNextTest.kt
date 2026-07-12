package tech.kzen.auto.common.paradigm.flow

import tech.kzen.auto.common.paradigm.flow.FlowStructureTestBuilder.awaitingNext
import tech.kzen.auto.common.paradigm.flow.FlowStructureTestBuilder.edge
import tech.kzen.auto.common.paradigm.flow.FlowStructureTestBuilder.exhausted
import tech.kzen.auto.common.paradigm.flow.FlowStructureTestBuilder.matrixOf
import tech.kzen.auto.common.paradigm.flow.FlowStructureTestBuilder.produced
import tech.kzen.auto.common.paradigm.flow.FlowStructureTestBuilder.vertex
import tech.kzen.auto.common.paradigm.flow.FlowStructureTestBuilder.visualOf
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowDag
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualFlowModel
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.EdgeOrientation
import tech.kzen.auto.common.paradigm.flow.util.FlowUtils
import tech.kzen.lib.common.model.location.ObjectLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


/**
 * Pins [FlowUtils.next]'s vertex selection: the last in-progress layer wins over the first ready
 * layer, min-epoch tie-break within a layer, and input readiness gating.
 */
class FlowUtilsNextTest {
    //-----------------------------------------------------------------------------------------------------------------
    private fun next(matrix: FlowMatrix, visualFlowModel: VisualFlowModel): ObjectLocation? {
        return FlowUtils.next(matrix, FlowDag.of(matrix), visualFlowModel)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun sourceOfPendingFlowSelectedFirst() {
        val source = vertex("source", 0, 0)
        val sink = vertex("sink", 2, 0, "input")
        val matrix = matrixOf(source, edge(EdgeOrientation.TopToBottom, 1, 0), sink)

        val nextVertex = next(matrix, visualOf(
                source to VisualVertexModel.empty,
                sink to VisualVertexModel.empty))

        assertEquals(source.objectLocation, nextVertex)
    }


    @Test
    fun successorSelectedAfterSourceProduces() {
        val source = vertex("source", 0, 0)
        val sink = vertex("sink", 2, 0, "input")
        val matrix = matrixOf(source, edge(EdgeOrientation.TopToBottom, 1, 0), sink)

        val nextVertex = next(matrix, visualOf(
                source to produced(),
                sink to VisualVertexModel.empty))

        assertEquals(sink.objectLocation, nextVertex)
    }


    @Test
    fun lastInProgressLayerWinsOverEarlierReadyLayer() {
        // Layer 0 holds a pending (ready) standalone source; layer 1 holds a mid-stream vertex.
        // The in-progress layer is selected even though it comes after the ready one.
        val standalone = vertex("standalone", 0, 0)
        val source = vertex("source", 0, 1)
        val streaming = vertex("streaming", 2, 1, "input")
        val matrix = matrixOf(
                standalone, source, edge(EdgeOrientation.TopToBottom, 1, 1), streaming)

        val nextVertex = next(matrix, visualOf(
                standalone to VisualVertexModel.empty,
                source to produced(),
                streaming to awaitingNext()))

        assertEquals(streaming.objectLocation, nextVertex)
    }


    @Test
    fun minEpochTieBreakWithinLayer() {
        // Two mid-stream sources in the same layer: the one with fewer executions goes next.
        val ahead = vertex("ahead", 0, 0)
        val behind = vertex("behind", 0, 1)
        val matrix = matrixOf(ahead, behind)

        val nextVertex = next(matrix, visualOf(
                ahead to awaitingNext(epoch = 2),
                behind to awaitingNext(epoch = 1)))

        assertEquals(behind.objectLocation, nextVertex)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun unwiredRequiredInputIsNeverReady() {
        // The fan-in's required input "b" has no wired predecessor: permanently not-ready (the
        // structure lint reports this before a run gets this far), so the flow stalls.
        val source = vertex("source", 0, 0)
        val fanIn = vertex("fan-in", 1, 0, "a", "b")
        val matrix = matrixOf(source, fanIn)

        val nextVertex = next(matrix, visualOf(
                source to produced(),
                fanIn to VisualVertexModel.empty))

        assertNull(nextVertex)
    }


    @Test
    fun unwiredOptionalInputDoesNotGate() {
        val source = vertex("source", 0, 0)
        val fanIn = vertex("fan-in", 1, 0, "a", "?b")
        val matrix = matrixOf(source, fanIn)

        val nextVertex = next(matrix, visualOf(
                source to produced(),
                fanIn to VisualVertexModel.empty))

        assertEquals(fanIn.objectLocation, nextVertex)
    }


    @Test
    fun onlyOptionalInputWiredRuns() {
        // Both inputs optional, only the second one wired — the AppendText "possibly one" shape.
        val source = vertex("source", 0, 1)
        val append = vertex("append", 1, 0, "?a", "?b")
        val matrix = matrixOf(source, append)

        val nextVertex = next(matrix, visualOf(
                source to produced(),
                append to VisualVertexModel.empty))

        assertEquals(append.objectLocation, nextVertex)
    }


    @Test
    fun selectLastRunsWhenOnlyOneWiredOptionalHasMessage() {
        // The FizzBuzz SelectLast pattern: both optional inputs wired, but per pass only one
        // branch produces (a filter drops the other). An empty wired optional must not gate —
        // its upstream's layer has already settled, so it will produce nothing this pass.
        val steady = vertex("steady", 0, 0)
        val filtered = vertex("filtered", 0, 1)
        val select = vertex("select", 1, 0, "?first", "?second")
        val matrix = matrixOf(steady, filtered, select)

        val nextVertex = next(matrix, visualOf(
                steady to produced(),
                filtered to exhausted(),
                select to VisualVertexModel.empty))

        assertEquals(select.objectLocation, nextVertex)
    }


    @Test
    fun vertexWithNoInputMessageIsNotReady() {
        // With nothing to consume anywhere (the only wired optional's upstream is spent without
        // a message), the vertex isn't ready and the flow settles.
        val source = vertex("source", 0, 1)
        val append = vertex("append", 1, 0, "?a", "?b")
        val matrix = matrixOf(source, append)

        val nextVertex = next(matrix, visualOf(
                source to exhausted(),
                append to VisualVertexModel.empty))

        assertNull(nextVertex)
    }


    @Test
    fun exhaustedPredecessorWithoutMessageDoesNotFeedSuccessor() {
        val source = vertex("source", 0, 0)
        val sink = vertex("sink", 2, 0, "input")
        val matrix = matrixOf(source, edge(EdgeOrientation.TopToBottom, 1, 0), sink)

        val nextVertex = next(matrix, visualOf(
                source to exhausted(),
                sink to VisualVertexModel.empty))

        assertNull(nextVertex)
    }


    @Test
    fun fanInGatesUntilEveryWiredInputHasMessage() {
        // Layer readiness applies the same per-input rule as in-layer selection: the fan-in is not
        // ready while wired input "b"'s upstream holds no message, even though "a"'s does.
        val left = vertex("left", 0, 0)
        val right = vertex("right", 0, 1)
        val fanIn = vertex("fan-in", 1, 0, "a", "b")
        val matrix = matrixOf(left, right, fanIn)

        val nextVertex = next(matrix, visualOf(
                left to produced(),
                right to exhausted(),
                fanIn to VisualVertexModel.empty))

        assertNull(nextVertex)
    }


    @Test
    fun inProgressSingleVertexLayerSelectedWithoutInputCheck() {
        // A mid-stream vertex re-executes to emit its next item without fresh inputs (e.g. a
        // repeater draining internal state), so the in-progress path must not gate on upstream
        // messages: nextInLayer's single-vertex shortcut keeps that behaviour.
        val source = vertex("source", 0, 0)
        val streaming = vertex("streaming", 1, 0, "input")
        val matrix = matrixOf(source, streaming)

        val nextVertex = next(matrix, visualOf(
                source to exhausted(),
                streaming to awaitingNext()))

        assertEquals(streaming.objectLocation, nextVertex)
    }
}
