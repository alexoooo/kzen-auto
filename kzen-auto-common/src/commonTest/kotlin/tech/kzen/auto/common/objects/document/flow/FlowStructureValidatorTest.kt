package tech.kzen.auto.common.objects.document.flow

import tech.kzen.auto.common.paradigm.flow.FlowStructureTestBuilder.edge
import tech.kzen.auto.common.paradigm.flow.FlowStructureTestBuilder.matrixOf
import tech.kzen.auto.common.paradigm.flow.FlowStructureTestBuilder.vertex
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.EdgeOrientation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/**
 * Geometry findings of [FlowStructureValidator.validateStructure] (one case per finding type).
 * The name findings (empty / duplicate parameter and result names) need inheritance-resolved
 * notation, so they are covered by FlowNotationTest's compile-failure cases on the jvm side.
 */
class FlowStructureValidatorTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun cleanChainHasNoFindings() {
        val findings = FlowStructureValidator.validateStructure(matrixOf(
                vertex("source", 0, 0),
                edge(EdgeOrientation.TopToBottom, 1, 0),
                vertex("sink", 2, 0, "input")))

        assertEquals(listOf(), findings)
    }


    @Test
    fun partiallyWiredOptionalInputsAreFine() {
        // The AppendText "possibly one" shape: one optional input wired, the other left open.
        val findings = FlowStructureValidator.validateStructure(matrixOf(
                vertex("source", 0, 1),
                vertex("append", 1, 0, "?a", "?b")))

        assertEquals(listOf(), findings)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun unwiredRequiredInputReported() {
        val findings = FlowStructureValidator.validateStructure(matrixOf(
                vertex("source", 0, 0),
                vertex("fan-in", 1, 0, "a", "b")))

        assertEquals(1, findings.size)
        assertTrue(findings.single().contains("Required input 'b' of 'fan-in'"))
    }


    @Test
    fun fullyUnwiredOptionalVertexReported() {
        val findings = FlowStructureValidator.validateStructure(matrixOf(
                vertex("append", 1, 0, "?a", "?b")))

        assertEquals(1, findings.size)
        assertTrue(findings.single().contains("None of the inputs of 'append'"))
    }


    @Test
    fun danglingPipeRunReported() {
        // Same severed run as FlowMatrixTest.brokenPipeRunTracesToNull: no pipe cell lies on a
        // functioning source-to-input run, and the sink's required input goes unwired.
        val findings = FlowStructureValidator.validateStructure(matrixOf(
                vertex("source", 0, 2),
                edge(EdgeOrientation.TopToLeft, 1, 2),
                edge(EdgeOrientation.TopToBottom, 1, 1),
                edge(EdgeOrientation.RightToBottom, 1, 0),
                vertex("sink", 2, 0, "input")))

        assertEquals(4, findings.size)
        assertTrue(findings.any { it.contains("Required input 'input' of 'sink'") })
        assertEquals(3, findings.count { it.startsWith("Pipe at") })
    }


    @Test
    fun excessIncomingConnectionsReported() {
        // Sources above both fan-in input columns wire them; the third source (beyond the span)
        // still finds the fan-in as a successor via the leftward scan — a third incoming
        // connection with nowhere to land.
        val findings = FlowStructureValidator.validateStructure(matrixOf(
                vertex("first", 0, 1),
                vertex("second", 0, 2),
                vertex("third", 0, 3),
                vertex("fan-in", 1, 1, "a", "b")))

        assertEquals(1, findings.size)
        assertTrue(findings.single().contains("'fan-in' has 3 incoming connections but only 2 input(s)"))
    }
}
