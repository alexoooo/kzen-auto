package tech.kzen.auto.common.objects.document.flow

import tech.kzen.auto.common.paradigm.flow.model.structure.FlowDag
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.EdgeDescriptor
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation


/**
 * Pre-run lint of a Flow's grid structure: geometry mistakes (a misplaced pipe silently rewires or
 * disconnects the flow) surface as findings at definition/compile time instead of a stalled run or a
 * mid-run check failure. Pure and shared: FlowLogicCompiler refuses to compile on findings (server),
 * and FlowController renders the same findings in a banner above the grid (client).
 *
 * There is no cycle finding: successor hops move laterally within a pipe row or strictly downward,
 * so a matrix-derived DAG cannot contain one.
 */
object FlowStructureValidator {
    //-----------------------------------------------------------------------------------------------------------------
    fun validate(
        flowMainLocation: ObjectLocation,
        graphNotation: GraphNotation,
        matrix: FlowMatrix
    ): List<String> {
        return validateStructure(matrix) + validateNames(graphNotation, flowMainLocation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** Geometry findings, from the matrix alone. */
    fun validateStructure(matrix: FlowMatrix): List<String> {
        val findings = mutableListOf<String>()
        val connectedEdges = mutableSetOf<EdgeDescriptor>()

        for (vertexDescriptor in matrix.verticesByLocation.values) {
            val vertexName = vertexDescriptor.objectLocation.objectPath.name.value
            var wiredCount = 0

            for ((inputIndex, inputName) in vertexDescriptor.inputNames.withIndex()) {
                val wiredPredecessor = matrix.traceVertexBackFrom(vertexDescriptor, inputName)

                if (wiredPredecessor == null) {
                    if (inputName in vertexDescriptor.requiredInputNames) {
                        findings.add(
                            "Required input '${inputName.value}' of '$vertexName' is not connected")
                    }
                    continue
                }

                wiredCount++
                connectedEdges.addAll(
                    matrix.traceEdgeBackFrom(vertexDescriptor, inputIndex))
            }

            // All-optional vertex with nothing wired: every input would arrive empty, which the
            // runner rejects (all-required cases are already reported input-by-input above).
            if (vertexDescriptor.inputNames.isNotEmpty() &&
                    vertexDescriptor.requiredInputNames.isEmpty() &&
                    wiredCount == 0) {
                findings.add(
                    "None of the inputs of '$vertexName' are connected")
            }
        }

        // Geometry can wire more predecessors onto a vertex than it has inputs (e.g. a source
        // beyond a fan-in's column span still finds it as a successor): the extra output would be
        // silently dropped.
        val flowDag = FlowDag.of(matrix)
        for (vertexDescriptor in matrix.verticesByLocation.values) {
            val predecessorCount = flowDag.predecessors[vertexDescriptor.objectLocation]?.size ?: 0
            if (predecessorCount > vertexDescriptor.inputNames.size) {
                findings.add(
                    "'${vertexDescriptor.objectLocation.objectPath.name.value}' has $predecessorCount" +
                    " incoming connections but only ${vertexDescriptor.inputNames.size} input(s)")
            }
        }

        // A pipe cell on no functioning source-to-input run connects nothing: the flow it appears
        // to carry doesn't exist.
        for (row in matrix.rows) {
            for (cell in row) {
                if (cell is EdgeDescriptor && cell !in connectedEdges) {
                    findings.add(
                        "Pipe at row ${cell.coordinate.row}, column ${cell.coordinate.column}" +
                        " does not connect anything")
                }
            }
        }

        return findings
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** Signature-name findings, from notation alone. */
    fun validateNames(
        graphNotation: GraphNotation,
        flowMainLocation: ObjectLocation
    ): List<String> {
        val findings = mutableListOf<String>()

        val parameterNames = FlowConventions
            .inputParameterNames(graphNotation, flowMainLocation, filterEmpty = false)
        val resultNames = FlowConventions
            .outputResultNames(graphNotation, flowMainLocation, filterEmpty = false)

        if (parameterNames.any { it.isEmpty() }) {
            findings.add("Flow input vertex is missing its 'parameter' name")
        }
        if (resultNames.any { it.isEmpty() }) {
            findings.add("Flow output vertex is missing its 'result' name")
        }

        duplicates(parameterNames).forEach {
            findings.add("Duplicate input parameter name: '$it'")
        }
        duplicates(resultNames).forEach {
            findings.add("Duplicate output result name: '$it'")
        }

        return findings
    }


    private fun duplicates(names: List<String>): List<String> {
        return names
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .toList()
    }
}
