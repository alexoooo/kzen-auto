package tech.kzen.auto.server.service.impl

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.logic.MoveToRefusal
import tech.kzen.auto.server.exec.RepositionDiagnostic
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.MoveTarget
import tech.kzen.lib.common.exec.engine.Node
import tech.kzen.lib.common.exec.engine.Repositionable
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


/**
 * The move-to (Set Next Statement) reposition gate: resolves the frame a request addresses in the engine's
 * node tree and asks every frame on the path whether it can honour its own role (logic-spec §4) — each
 * transit hop descending through the call-site of the hop below it, the addressed frame moving to the
 * target — so a move no frame on the path can carry out is refused before anything is torn down.
 *
 * Pure gate: it only reads the node snapshot and compiles hop definitions via the supplied callback,
 * mutating no run state — [ServerLogicController.moveToAttempt] owns the barrier that acts on an accepted
 * request.
 *
 * Capability-based and flavour-blind, which is the whole point of asking each frame about its own
 * structure: today only a Script answers yes, so a Flow or Job hop refuses cleanly instead of silently
 * parking at the element that hosts the destination. The reason is asked for the same way — a hop that
 * declares a [RepositionDiagnostic] explains its own refusal, and one that doesn't gets a generic reason
 * naming it.
 */
class RepositionGate(
    private val objectStableMapper: ObjectStableMapper
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(RepositionGate::class.java)
    }


    // The outcome of the per-hop gate: the request to carry across the barrier, or the reason it was
    // refused (which travels on as the control reply's reason).
    sealed interface Attempt {
        data class Accepted(val moveTarget: MoveTarget): Attempt

        data class Refused(val reason: String): Attempt
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The chain of nodes root -> the frame [executionId] names, collected on the way DOWN: [Node] has no parent
    // pointer, parentage is the [Node.children] nesting alone. A null [executionId] addresses the root frame, so
    // the chain is that node by itself. Null when no node carries the id — node ids are monotone and never
    // reused, so a stale id from a client poll resolves to nothing instead of to some unrelated later frame.
    fun framePathTo(node: Node, executionId: LogicExecutionId?): List<Node>? {
        if (executionId == null || node.id.value == executionId.value) {
            return listOf(node)
        }
        for (child in node.children) {
            val childPath = framePathTo(child, executionId)
            if (childPath != null) {
                return listOf(node) + childPath
            }
        }
        return null
    }


    /**
     * The per-hop capability gate. [compileHop] compiles a non-root hop's [Logic] from the current
     * definition (the root's is [rootLogic], already recompiled for this barrier — compiling it again would
     * build a second definition of the same document only to throw it away); a compile failure refuses the
     * hop rather than throwing.
     */
    fun repositionRequest(
        framePath: List<Node>,
        rootLocation: ObjectLocation,
        rootLogic: Logic,
        compileHop: (ObjectLocation) -> Logic,
        targetId: ObjectStableId
    ): Attempt {
        val callSitePath = mutableListOf<ObjectStableId>()

        for ((index, node) in framePath.withIndex()) {
            // The root frame survives a deleted root document by falling back to where the run started, as
            // the controller's status() does — so a move is refused for a real structural reason, never for
            // a stale id.
            val hopLocation = objectStableMapper.objectLocationOrNull(node.stableId)
                ?: rootLocation.takeIf { index == 0 }
                ?: return Attempt.Refused(MoveToRefusal.frameDocumentMissing())

            val hopName = hopLocation.documentPath.name.value

            val hopLogic =
                if (index == 0) {
                    rootLogic
                }
                else {
                    try {
                        compileHop(hopLocation)
                    }
                    catch (e: Throwable) {
                        logger.warn("Unable to compile frame for move-to: {}", hopLocation, e)
                        return Attempt.Refused(
                            "Unable to compile $hopName: ${e.message ?: e::class.simpleName}")
                    }
                }

            if (hopLogic !is Repositionable) {
                return Attempt.Refused(MoveToRefusal.frameCannotReposition(hopName))
            }

            val hostedHop = framePath.getOrNull(index + 1)
            if (hostedHop == null) {
                if (!hopLogic.canMoveTo(targetId)) {
                    return Attempt.Refused(
                        (hopLogic as? RepositionDiagnostic)?.moveToRefusal(targetId)
                            ?: "$hopName doesn't support moving to that step")
                }
            }
            else {
                // Addressability: null is not a wildcard. A host that names no distinct call-site matches no
                // hop of a path, so the frame it opened cannot be path-addressed at all.
                val callSite = hostedHop.callerStableId
                    ?: return Attempt.Refused(MoveToRefusal.frameCallSiteUnknown(hopName))

                if (!hopLogic.canDescendThrough(callSite)) {
                    return Attempt.Refused(
                        (hopLogic as? RepositionDiagnostic)?.descendRefusal(callSite)
                            ?: "$hopName can't continue into the nested logic")
                }
                callSitePath.add(callSite)
            }
        }

        return Attempt.Accepted(MoveTarget(targetId, callSitePath))
    }
}
