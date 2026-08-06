package tech.kzen.auto.server.exec.script

import tech.kzen.auto.common.objects.document.logic.context.ContextAddressing
import tech.kzen.auto.common.objects.document.logic.context.ContextDescriptor
import tech.kzen.auto.common.objects.document.logic.context.LogicContextConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptJumpAnalysis
import tech.kzen.auto.common.objects.document.script.model.ScriptJumpRefusal
import tech.kzen.auto.common.paradigm.logic.MoveToRefusal
import tech.kzen.auto.server.exec.LogicParameter
import tech.kzen.auto.server.exec.LogicParameterTrace
import tech.kzen.auto.server.exec.RepositionDiagnostic
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.engine.Repositionable
import tech.kzen.lib.common.exec.engine.context.ExportSelector
import tech.kzen.lib.common.exec.engine.restoredAs
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * A Script as a [Logic]: run the root step sequence to completion and return its result — the value a
 * [ResultStep][tech.kzen.auto.server.objects.script.step.eval.ResultStep] captured, or else the last root step's
 * value. A Script declaring no `main` result is void, and its steps' values are discarded.
 *
 * The steps themselves are the `@Reflect` notation archetypes resolved from [ScriptRunStructure.graphInstance];
 * this class only seeds the parameters and the live-edit carry-over, then drives the spine
 * ([ScriptRunContext.runSteps]) over the root step locations. No step types are enumerated here — adding a step
 * type (Browser, or a third-party step) requires no change to this class or the compiler.
 */
class ScriptLogic(
    private val rootStepLocations: List<ObjectLocation>,
    private val parameters: List<LogicParameter>,
    private val structure: ScriptRunStructure,
    private val logicSignature: LogicSignature
): Logic, Repositionable, RepositionDiagnostic {
    override fun signature(): LogicSignature {
        return logicSignature
    }


    /**
     * A move-to (Set Next Statement) target is jumpable iff it resolves to a step in this Script's root document
     * that [ScriptJumpAnalysis] accepts (a real step, not a binding, not inside a loop body). Static structural
     * check only — the controller gates on this before rebuilding, so an unsupported target is rejected without
     * tearing the run down.
     */
    override fun canMoveTo(target: ObjectStableId): Boolean {
        val targetLocation = structure.objectStableMapper.objectLocationOrNull(target)
            ?: return false
        if (targetLocation.documentPath != structure.scriptLocation.documentPath) {
            return false
        }
        return ScriptJumpAnalysis.isValidTarget(
            structure.graphNotation, targetLocation.documentPath, structure.scriptTree, targetLocation.objectPath)
    }


    /**
     * A call-site this Script hosts the addressed frame from is descendable iff it resolves to a step in this
     * Script's own document that the spine walks ([ScriptJumpAnalysis.isDescendableCallSite]) — so the rebuilt
     * spine runs to that RunStep with its boundary suppressed and hosts it, instead of parking there short of
     * the frame the move addresses. Deliberately laxer than [canMoveTo]: a transit frame repositions nothing of
     * its own, so a call-site inside a loop body qualifies — the loop re-enters at its carried cursor and the
     * descent rides that resumed iteration. Static structural check only, gated by the controller before the
     * barrier, exactly like [canMoveTo].
     */
    override fun canDescendThrough(callSite: ObjectStableId): Boolean {
        val callSiteLocation = structure.objectStableMapper.objectLocationOrNull(callSite)
            ?: return false
        if (callSiteLocation.documentPath != structure.scriptLocation.documentPath) {
            return false
        }
        return ScriptJumpAnalysis.isDescendableCallSite(
            structure.graphNotation, callSiteLocation.documentPath, structure.scriptTree,
            callSiteLocation.objectPath)
    }


    override fun moveToRefusal(target: ObjectStableId): String? {
        if (canMoveTo(target)) {
            return null
        }

        val targetLocation = structure.objectStableMapper
            .objectLocationOrNull(target)
            ?.takeIf { it.documentPath == structure.scriptLocation.documentPath }
            ?: return MoveToRefusal.targetNotJumpable()

        return ScriptJumpRefusal.reason(
            structure.graphNotation, targetLocation.documentPath, structure.scriptTree,
            targetLocation.objectPath)
    }


    override fun descendRefusal(callSite: ObjectStableId): String? {
        if (canDescendThrough(callSite)) {
            return null
        }

        val callSiteLocation = structure.objectStableMapper
            .objectLocationOrNull(callSite)
            ?.takeIf { it.documentPath == structure.scriptLocation.documentPath }
            ?: return MoveToRefusal.frameCallSiteUnknown(documentName())

        return MoveToRefusal.frameCannotResume(documentName(), callSiteLocation.objectPath.name.value)
    }


    private fun documentName(): String {
        return structure.scriptLocation.documentPath.name.value
    }


    override suspend fun run(execution: Execution): TupleValue {
        val context = ScriptRunContext(execution, structure)

        // Contexts this document EXPORTS (logic-spec §6): declared BEFORE any step runs and before any child is
        // hosted, so a provide anywhere below climbs through this frame when it is on an export chain. Every
        // Script's own Logic does this — root and hosted alike — which is why hosting needs no knowledge of the
        // child's notation. A migrate rebuild re-runs `run`, so re-declaration is free.
        // A DECLARED qualifier exports exactly the member it names; an unqualified declaration exports the
        // whole family, because a computed qualifier is the only thing it could otherwise mean. Collapsing the
        // first onto the second would carry a sibling nobody offered upward.
        for (export in LogicContextConventions.documentExports(
                structure.graphNotation, structure.scriptLocation.documentPath)) {
            execution.declareExport(exportSelectorOf(export))
        }

        // A document declaring `context.requires` cannot work without a caller that supplies it, so fail at run
        // start rather than three steps in. Sound for the Script spine because a caller's provides always
        // precede its RunStep positionally.
        for (required in LogicContextConventions.documentRequires(
                structure.graphNotation, structure.scriptLocation.documentPath)) {
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

        // Live-edit migration (logic-spec §5): adopt the predecessor run's completed work (read once at start) so
        // the spine replays-short-circuits completed steps, and register the capture so a later edit carries this
        // run's completed work forward. Null on a fresh run -> nothing to adopt, everything runs live.
        //
        // A migration may also carry a repositioning request (Set Next Statement, logic-spec §4) in one of its
        // two roles: this frame is the one it ADDRESSES (a move target — restore applies the jump surgery), or a
        // TRANSIT frame on the way there (a call-site to descend through). The descend obligation alone is
        // enough to need a restore: a transit frame that carried no capture still owes the descent, and skipping
        // it would park the rebuild at the hosting RunStep instead.
        val migrationState = execution.restoredAs<ScriptMigrationState>()
        val moveDescendCallSite = execution.moveDescendCallSite
        if (migrationState != null || moveDescendCallSite != null) {
            context.restore(
                migrationState, execution.moveTarget, moveDescendCallSite, execution.removedStableIds)
        }
        execution.onCapture { context.captureState() }

        for (parameter in parameters) {
            val value = parameter.resolve(execution.inputs)
            context.recordValue(parameter.stableId, value)
            LogicParameterTrace.emit(execution, parameter.stableId, value)
        }

        val lastStepValue = context.runSteps(rootStepLocations)

        // Control flow: an End Script signal (return) that unwound to the root is consumed here; a Skip / Finish
        // reaching the root is a mistargeted control step and fails the run (validation should prevent it).
        context.consumeRootSignalOrFail()

        // A Result step's captured value wins — including one raised from a nested branch, which sets the result
        // before ending the Script. Otherwise the last root step's value IS the result, mirroring the way a
        // ForEachStep collects its body's terminal value.
        return context.result()
            ?: implicitResult(lastStepValue)
    }


    /** A Script declaring no `main` result is void, so there is nothing for the last root step's value to fill. */
    private fun implicitResult(lastStepValue: Any?): TupleValue {
        return when (structure.resultSignature.find(TupleComponentName.main)) {
            null -> TupleValue.empty
            else -> TupleValue.ofMain(lastStepValue)
        }
    }


    private fun exportSelectorOf(descriptor: ContextDescriptor): ExportSelector {
        val key = ContextAddressing.keyOf(descriptor)
        return when (key.qualifier) {
            null -> ExportSelector.Family(key.family)
            else -> ExportSelector.Exact(key)
        }
    }
}
