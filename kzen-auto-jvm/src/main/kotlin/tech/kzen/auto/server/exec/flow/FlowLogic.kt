package tech.kzen.auto.server.exec.flow

import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


/**
 * A Flow document as a [Logic]: walk its dataflow DAG to completion on the new engine and return the result
 * harvested from the output vertices. Holds only the immutable compiled structure ([childLogics] for the
 * logic-host callees, the [logicSignature], and the services the per-vertex mechanics need); the mutable
 * per-run state lives in a fresh [FlowRun] created per [run] call, so one [FlowLogic] can be hosted more than
 * once (a [FlowLogicHost][tech.kzen.auto.common.paradigm.flow.api.FlowLogicHost] vertex pointing at a Flow).
 *
 * The Flow analogue of [tech.kzen.auto.server.exec.script.ScriptLogic] — but where a Script is a sequential
 * spine, a Flow is a vertex DAG, so [FlowRun] (not a tree of step objects) drives the per-vertex stepping.
 */
class FlowLogic(
    private val documentPath: DocumentPath,
    private val graphDefinition: GraphDefinition,
    private val childLogics: Map<ObjectStableId, FlowChildLogic>,
    private val logicSignature: LogicSignature,
    private val objectStableMapper: ObjectStableMapper,
    private val graphEnvironment: GraphEnvironment
): Logic {
    override fun signature(): LogicSignature {
        return logicSignature
    }


    override suspend fun run(execution: Execution): TupleValue {
        return FlowRun(
            execution,
            documentPath,
            graphDefinition,
            childLogics,
            objectStableMapper,
            graphEnvironment
        ).run()
    }
}
