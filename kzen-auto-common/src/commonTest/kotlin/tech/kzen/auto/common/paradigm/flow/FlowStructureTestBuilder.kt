package tech.kzen.auto.common.paradigm.flow

import tech.kzen.auto.common.paradigm.flow.model.exec.VisualFlowModel
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.CellCoordinate
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.CellDescriptor
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.EdgeDescriptor
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.EdgeOrientation
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.VertexDescriptor
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.platform.collect.toPersistentMap


/**
 * Hand-builds [FlowMatrix] fixtures for the structure-core tests: pure descriptors, no notation or graph
 * metadata ([FlowMatrix.ofDocument] needs a server-built GraphStructure; the geometry itself doesn't).
 * [matrixOf] sorts-and-groups cells the same way FlowMatrix's ofUnorderedDescriptors produces its rows,
 * which [FlowMatrix.get] relies on.
 */
object FlowStructureTestBuilder {
    //-----------------------------------------------------------------------------------------------------------------
    private val testDocument = DocumentPath.parse("test/flow-structure-test.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    fun location(name: String): ObjectLocation {
        return ObjectLocation(testDocument, ObjectPath.parse(name))
    }


    /** An input name prefixed with "?" declares an OptionalInput; all others are RequiredInput. */
    fun vertex(name: String, row: Int, column: Int, vararg inputNames: String): VertexDescriptor {
        val allInputs = inputNames.map { AttributeName(it.removePrefix("?")) }
        val requiredInputs = inputNames
                .filter { ! it.startsWith("?") }
                .map { AttributeName(it) }

        return VertexDescriptor(
                location(name),
                allInputs,
                requiredInputs,
                row * 100 + column,
                CellCoordinate(row, column))
    }


    fun edge(orientation: EdgeOrientation, row: Int, column: Int): EdgeDescriptor {
        return EdgeDescriptor(
                orientation,
                row * 100 + column,
                CellCoordinate(row, column))
    }


    fun matrixOf(vararg cells: CellDescriptor): FlowMatrix {
        val sortedByRowThenColumn = cells.sortedWith(CellDescriptor.byRowThenColumn)

        val matrix = mutableListOf<List<CellDescriptor>>()
        val row = mutableListOf<CellDescriptor>()
        var previousRow = -1
        for (cell in sortedByRowThenColumn) {
            if (previousRow != cell.coordinate.row && row.isNotEmpty()) {
                matrix.add(row.toList())
                row.clear()
            }
            row.add(cell)
            previousRow = cell.coordinate.row
        }
        if (row.isNotEmpty()) {
            matrix.add(row.toList())
        }
        return FlowMatrix(matrix)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun visualOf(vararg states: Pair<VertexDescriptor, VisualVertexModel>): VisualFlowModel {
        return VisualFlowModel(states
                .associate { it.first.objectLocation to it.second }
                .toPersistentMap())
    }


    /** Executed and holding an unconsumed output message. */
    fun produced(epoch: Int = 1, hasNext: Boolean = false): VisualVertexModel {
        return VisualVertexModel(false, null, ExecutionValue.of("message"), hasNext, epoch, null)
    }


    /** Executed mid-stream: output already consumed (or none this epoch), more remaining. */
    fun awaitingNext(epoch: Int = 1): VisualVertexModel {
        return VisualVertexModel(false, null, null, true, epoch, null)
    }


    /** Executed to completion without a retained message. */
    fun exhausted(epoch: Int = 1): VisualVertexModel {
        return VisualVertexModel(false, null, null, false, epoch, null)
    }


    /** Failed: parked under pause-on-error, or settled failed. */
    fun errored(epoch: Int = 0): VisualVertexModel {
        return VisualVertexModel(false, null, null, false, epoch, "test failure")
    }
}
