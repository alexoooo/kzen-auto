package tech.kzen.auto.server.exec.flow

import tech.kzen.auto.common.objects.document.logic.context.ContextAddressing
import tech.kzen.auto.common.objects.document.logic.context.ContextDescriptor
import tech.kzen.auto.common.objects.document.logic.context.LogicContextConventions
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.engine.context.ExportSelector
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
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


    override suspend fun run(execution: Execution): DataBindings {
        // Contexts this document EXPORTS (logic-spec §6): declared BEFORE any vertex runs and before any child
        // is hosted, so a provide anywhere below climbs through this frame when it is on an export chain. The
        // same prologue every Script's Logic runs, and for the same reason — hosting needs no knowledge of the
        // child's notation. A migrate rebuild re-runs `run`, so re-declaration is free.
        //
        // A Flow binds nothing of its own (no per-vertex `binds`), so in practice an export here backs a
        // Context that a hosted child provides and offers upward. Declaring it is what lets that offer climb
        // past the Flow frame instead of resting on it — without this a Flow is an export BARRIER.
        for (export in LogicContextConventions.documentExports(graphNotation, documentPath)) {
            execution.declareExport(exportSelectorOf(export))
        }

        // A document declaring `context.requires` cannot work without a caller that supplies it, so fail at run
        // start rather than partway through the DAG. Note this gate is what makes a Flow usable as a callee at
        // all: it is the assertion that a host vertex's (or RunStep's) `contexts:` map actually reached it.
        for (required in LogicContextConventions.documentRequires(graphNotation, documentPath)) {
            val key = ContextAddressing.keyOf(required)
            val open =
                if (key.qualifier != null) {
                    execution.hasBinding(key)
                }
                else {
                    execution.hasBindingInFamily(key.family)
                }

            check(open) {
                "Requires ${required.label()}: not provided by caller"
            }
        }

        return FlowRun(
            execution,
            documentPath,
            graphDefinition,
            childLogics,
            logicSignature.outputs,
            objectStableMapper,
            graphEnvironment
        ).run()
    }


    // A DECLARED qualifier exports exactly the member it names; an unqualified declaration exports the whole
    // family, because a computed qualifier is the only thing it could otherwise mean. Collapsing the first onto
    // the second would carry a sibling nobody offered upward.
    private fun exportSelectorOf(descriptor: ContextDescriptor): ExportSelector {
        val key = ContextAddressing.keyOf(descriptor)
        return when (key.qualifier) {
            null -> ExportSelector.Family(key.family)
            else -> ExportSelector.Exact(key)
        }
    }


    private val graphNotation: GraphNotation
        get() = graphDefinition.graphStructure.graphNotation
}
