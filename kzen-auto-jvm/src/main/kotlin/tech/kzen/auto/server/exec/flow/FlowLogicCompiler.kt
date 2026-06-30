package tech.kzen.auto.server.exec.flow

import tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix
import tech.kzen.auto.server.exec.LogicCompiler
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.objects.flow.vertex.FlowInputVertex
import tech.kzen.auto.server.objects.flow.vertex.FlowOutputVertex
import tech.kzen.auto.server.objects.flow.vertex.RunLogicVertex
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleComponentDefinition
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * Translates a Flow document's notation graph into a [FlowLogic] runnable on the new engine — the Flow
 * analogue of [tech.kzen.auto.server.exec.script.ScriptLogicCompiler]. It reads the same `vertices` / `edges`
 * dataflow shape through [FlowMatrix] that the executor uses; the per-vertex mechanics are deferred to
 * [FlowRun] at run time (a fresh instance per vertex execution, matching the old engine).
 *
 * The two structural derivations done here once (rather than per run): the Logic signature (inputs from
 * [FlowInputVertex] vertices, outputs from [FlowOutputVertex] vertices, in notation order — mirroring
 * [tech.kzen.auto.server.objects.flow.FlowDocument.define]) and the pre-compiled [RunLogicVertex] callees
 * (each compiled through [LogicCompiler], so a Flow can host a Script or another Flow).
 */
object FlowLogicCompiler {
    fun compile(
        flowLocation: ObjectLocation,
        graphNotation: GraphNotation,
        graphDefinition: GraphDefinition,
        services: LogicCompilerServices
    ): FlowLogic {
        val documentPath = flowLocation.documentPath
        val matrix = FlowMatrix.ofDocument(documentPath, graphDefinition.graphStructure)
        val graphInstance = GraphCreator.createGraph(
            graphDefinition.filterTransitive(documentPath), services.graphEnvironment)

        val inputs = mutableListOf<TupleComponentDefinition>()
        val outputs = mutableListOf<TupleComponentDefinition>()
        val childLogics = mutableMapOf<ObjectStableId, FlowChildLogic>()

        // Notation order (vertex index) so the derived signature matches FlowDocument.define().
        val orderedVertexLocations = matrix.verticesByLocation.values
            .sortedBy { it.indexInContainer }
            .map { it.objectLocation }

        for (vertexLocation in orderedVertexLocations) {
            when (val reference = graphInstance[vertexLocation]?.reference) {
                is FlowInputVertex ->
                    inputs.add(TupleComponentDefinition(reference.tupleComponentName, LogicType.any))

                is FlowOutputVertex ->
                    outputs.add(TupleComponentDefinition(reference.tupleComponentName, LogicType.any))

                is RunLogicVertex -> {
                    val childLogic = LogicCompiler.compile(
                        reference.instructions, graphNotation, graphDefinition, services)
                    childLogics[services.objectStableMapper.objectStableId(vertexLocation)] = FlowChildLogic(
                        services.objectStableMapper.objectStableId(reference.instructions),
                        childLogic,
                        childLogic.signature().inputs.components.firstOrNull()?.name)
                }

                else -> {}
            }
        }

        return FlowLogic(
            documentPath,
            graphDefinition,
            childLogics,
            LogicSignature(TupleDefinition(inputs), TupleDefinition(outputs)),
            services.objectStableMapper,
            services.flowMessageInspector,
            services.graphEnvironment)
    }
}
