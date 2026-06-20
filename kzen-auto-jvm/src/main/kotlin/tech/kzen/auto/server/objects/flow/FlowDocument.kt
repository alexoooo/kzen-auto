package tech.kzen.auto.server.objects.flow

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.paradigm.flow.api.FlowVertex
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.EdgeDescriptor
import tech.kzen.auto.common.paradigm.flow.service.format.FlowMessageInspector
import tech.kzen.auto.server.objects.flow.vertex.FlowInputVertex
import tech.kzen.auto.server.objects.flow.vertex.FlowOutputVertex
import tech.kzen.lib.common.exec.logic.Logic
import tech.kzen.lib.common.exec.logic.LogicControl
import tech.kzen.lib.common.exec.logic.LogicExecution
import tech.kzen.lib.common.exec.logic.LogicHandle
import tech.kzen.lib.common.exec.logic.LogicHandleFacade
import tech.kzen.lib.common.exec.logic.model.LogicDefinition
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle
import tech.kzen.lib.common.exec.tuple.TupleComponentDefinition
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


/**
 * The modernized "graph" / "time series" document, rebuilt on the kzen-lib Logic/Execution model so it
 * can be Run / Stepped / Paused / Resumed through the shared
 * [tech.kzen.auto.server.service.impl.ServerLogicController] (like a Script), and so it carries input
 * parameters and a return value.
 *
 * Input parameters and the result are modelled as dedicated graph vertices: each [FlowInputVertex]
 * contributes an input to the logic signature (read from the run's arguments at execution time), and
 * each [FlowOutputVertex] contributes an output (harvested into the result tuple). The DAG itself is
 * walked one vertex per step by [FlowExecution].
 */
@Reflect
class FlowDocument(
    private val vertices: List<FlowVertex<*>>,
    @Suppress("unused") private val edges: List<EdgeDescriptor>,
    private val selfLocation: ObjectLocation,

    @Service private val objectStableMapper: ObjectStableMapper,
    @Service private val graphCreator: GraphCreator,
    @Service private val environment: GraphEnvironment,
    @Service private val flowMessageInspector: FlowMessageInspector
):
    DocumentArchetype(),
    Logic
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun define(): LogicDefinition {
        // The external Logic signature stays `any`-typed for now (nothing consumes the declared types
        // yet), mirroring ScriptDocument.define().
        val inputs = vertices
            .filterIsInstance<FlowInputVertex>()
            .map { TupleComponentDefinition(it.tupleComponentName, LogicType.any) }

        val outputs = vertices
            .filterIsInstance<FlowOutputVertex>()
            .map { TupleComponentDefinition(it.tupleComponentName, LogicType.any) }

        return LogicDefinition(
            TupleDefinition(inputs),
            TupleDefinition(outputs))
    }


    override fun execute(
        logicHandle: LogicHandle,
        logicTraceHandle: LogicTraceHandle,
        logicRunExecutionId: LogicRunExecutionId,
        logicControl: LogicControl
    ): LogicExecution {
        return FlowExecution(
            selfLocation.documentPath,
            logicTraceHandle,
            // RunLogicVertex invokes a child Logic via this handle (like a Script's RunStep).
            LogicHandleFacade(logicRunExecutionId, logicHandle),
            objectStableMapper,
            graphCreator,
            environment,
            flowMessageInspector)
    }
}
