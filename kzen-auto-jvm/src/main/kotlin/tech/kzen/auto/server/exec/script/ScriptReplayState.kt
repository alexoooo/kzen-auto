package tech.kzen.auto.server.exec.script

import tech.kzen.auto.common.objects.document.script.model.ScriptJumpAnalysis
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * The replay / migration bookkeeping of one Script run (logic-spec §5): as each step finishes,
 * [recordCompleted] records its outcome (the capture source [captureState] snapshots at the migration
 * barrier); on the rebuilt run [restore] seeds the restored sets from the predecessor's capture so the
 * spine ([ScriptRunContext.runSteps]) can replay-short-circuit completed steps, and a re-running loop can
 * [dropReplay] its body's stale outcomes. A mid-flight step (a loop between iterations) additionally
 * carries opaque sub-state ([recordCarry] / [restoredCarry]) so it can resume where it left off — see
 * [ScriptMigrationState] for the carried shape and its bounds.
 *
 * MOVE-TO (Set Next Statement, logic-spec §4): a migration may carry a repositioning request, which
 * addresses ONE frame by call-site path, so [restore] has a role-specific path for each of the two ways
 * this frame can be named.
 *
 * As the ADDRESSED frame it carries a jump target and [restore] performs outcome-set surgery instead of a
 * plain restore (drop the target and everything at/after it — discarding the captures of any child
 * invocations those dropped steps hosted, which the re-run abandons — mark the pre-target skips
 * value-less, run the descend ancestors with their checkpoint suppressed), so the rebuilt paused spine
 * re-runs from / skips to the target and parks there.
 *
 * As a TRANSIT frame it carries only the call-site it must descend through, and [restore] is an ordinary
 * restore plus that call-site and its containers in the descend set — the rebuilt spine reaches the
 * hosting RunStep without parking at it and hosts the frame the move really addresses.
 *
 * Either way the surgery is computed by the notation-driven [ScriptJumpAnalysis], and the jump shares the
 * migrate barrier, so an edit-then-jump takes both in one rebuild.
 *
 * Confined, like its owning [ScriptRunContext] (the orchestrator that consults it), to the single run
 * coroutine — no synchronization.
 */
class ScriptReplayState(
    private val structure: ScriptRunStructure
) {
    //-----------------------------------------------------------------------------------------------------------------
    private val objectStableMapper get() = structure.objectStableMapper

    // The outcome each step that COMPLETED produced — the live-edit capture source. Excludes the non-step
    // bindings (a parameter / loop item), which the rebuilt run re-derives rather than carries.
    private val completedOutcomes = LinkedHashMap<ObjectStableId, Any?>()

    // The predecessor run's completed outcomes, seeded by [restore] across a live edit; consulted by the spine to
    // replay-short-circuit and pruned by a re-running loop ([dropReplay]). Empty on a fresh (non-migration) run.
    private val restoredOutcomes = HashMap<ObjectStableId, Any?>()

    // Move-to (Set Next Statement) surgery, seeded by [restore] when the migration named this frame: steps the
    // rebuilt spine short-circuits with NO value ([skippedSteps] — forward-skipped over; a later reference to
    // one error-parks via the spine's referencedValue) and steps the spine runs (re-evaluating an If's
    // condition) but does NOT park at ([descendSteps]), so the paused rebuild settles past them — at the jump
    // target inside its branch on the addressed frame, or inside the hosted frame beyond the call-site on a
    // transit frame. Both empty on an ordinary run / edit-migrate.
    private val skippedSteps = HashSet<ObjectStableId>()
    private val descendSteps = HashSet<ObjectStableId>()

    // The trace detail a partially-committed step brought with its value ([ScriptStep.partialOutcome]) — the
    // journal a loop the jump skipped over had built while it was running. Consulted by the spine's
    // adopt-outcome emit, which would otherwise blank it. Empty on an ordinary run / edit-migrate.
    private val partialDetails = HashMap<ObjectStableId, ExecutionValue>()

    // Opaque per-step mid-flight migration sub-state — a loop's iteration cursor — carried alongside
    // [completedOutcomes]: [carryStates] is the live capture source, [restoredCarries] the predecessor run's
    // carries seeded by [restore] (read via [restoredCarry], pruned by [dropReplay]).
    private val carryStates = LinkedHashMap<ObjectStableId, Any?>()
    private val restoredCarries = HashMap<ObjectStableId, Any?>()


    //-----------------------------------------------------------------------------------------------------------------
    fun isSkipped(stableId: ObjectStableId): Boolean {
        return stableId in skippedSteps
    }


    fun hasRestoredOutcome(stableId: ObjectStableId): Boolean {
        return restoredOutcomes.containsKey(stableId)
    }


    fun restoredOutcome(stableId: ObjectStableId): Any? {
        return restoredOutcomes[stableId]
    }


    fun partialDetailOrNull(stableId: ObjectStableId): ExecutionValue? {
        return partialDetails[stableId]
    }


    /** Claim-once: a descend obligation suppresses its step's checkpoint exactly one walk. */
    fun consumeDescend(stableId: ObjectStableId): Boolean {
        return descendSteps.remove(stableId)
    }


    fun recordCompleted(stableId: ObjectStableId, value: Any?) {
        completedOutcomes[stableId] = value
    }


    fun recordCarry(stableId: ObjectStableId, state: Any?) {
        if (state == null) {
            carryStates.remove(stableId)
        }
        else {
            carryStates[stableId] = state
        }
    }


    fun restoredCarry(stableId: ObjectStableId): Any? {
        return restoredCarries[stableId]
    }


    // The map half of the generic iteration reset (see [tech.kzen.auto.server.objects.script.api.StepExecution.dropReplay]):
    // beyond the replay set, also prunes the capture source — so a mid-iteration capture carries only the
    // current iteration's completed prefix — and the restored carries, so a nested loop's cursor from a
    // different enclosing iteration is never consumed by a later fresh pass. The engine-side discard / trace
    // reset stays with the orchestrator ([ScriptRunContext.dropReplay]).
    fun dropReplay(stableIds: List<ObjectStableId>) {
        for (stableId in stableIds) {
            restoredOutcomes.remove(stableId)
            restoredCarries.remove(stableId)
            completedOutcomes.remove(stableId)
        }
    }


    /** Snapshot the run's completed work for carry-over at the migration barrier (see [ScriptMigrationState]). */
    fun captureState(result: TupleValue?): ScriptMigrationState {
        return ScriptMigrationState(LinkedHashMap(completedOutcomes), LinkedHashMap(carryStates), result)
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Seed the carried-over completed work from the predecessor run's capture (read once at run start).
     * Returns the carried result value (decision 11 — kept even under a jump; a Result at/after the target
     * re-runs).
     *
     * Work an element the edit DELETED produced is dropped rather than carried ([removedStableIds], logic-spec
     * §5): a stable id is the element's address, so a step created where the deleted one stood mints the same
     * id and would otherwise be replay-adopted as Done — reported complete, holding the deleted step's value,
     * having never run. The same filter drops an id that resolves to nothing at all, which is that removal
     * seen from a run whose barrier could not report it (a deleted document).
     *
     * A [state] of null means nothing was carried (a fresh frame, or a predecessor that captured nothing) — the
     * restore then does only what the repositioning arguments ask of it.
     *
     * When the migration barrier carried a move-to [moveTarget] that resolves to a valid jump in the current
     * [ScriptTree][tech.kzen.auto.common.objects.document.script.model.ScriptTree] ([jumpPlanFor]), apply
     * outcome-set surgery instead of a plain restore — see the class KDoc for the two roles. Steps the walk
     * visits before the target but keeps no outcome for become [skippedSteps] (value-less), UNLESS one was
     * mid-flight and offers a partial value ([ScriptStep.partialOutcome] — a loop's collected iterations),
     * which is committed as a restored outcome instead; the ancestors become [descendSteps] (run, checkpoint
     * suppressed). A dropped step's hosted child invocations are additionally discarded ([discardCaptured]) —
     * the step re-runs, so its pre-jump sub-execution is abandoned and must not be adopted by the fresh one.
     * An unsupported / unresolvable [moveTarget] falls back to a full restore — the engine ignore-contract
     * (the controller's `canMoveTo` gate normally makes that unreachable).
     *
     * [discardCaptured] tells the engine which hosted-child captures are abandoned ([Execution.discardCaptured]);
     * [emitIdle] resets a dropped step's stale display through the orchestrator's single trace choke point.
     */
    fun restore(
        state: ScriptMigrationState?,
        moveTarget: ObjectStableId?,
        moveDescendCallSite: ObjectStableId?,
        removedStableIds: Set<ObjectStableId>,
        discardCaptured: (Set<ObjectStableId>) -> Unit,
        emitIdle: (ObjectStableId) -> Unit
    ): TupleValue? {
        val carriedOutcomes = state?.completedOutcomes.orEmpty().filterKeys { survivesEdit(it, removedStableIds) }
        val carriedCarries = state?.stepCarry.orEmpty().filterKeys { survivesEdit(it, removedStableIds) }

        val plan = moveTarget?.let { jumpPlanFor(it) }
        if (plan == null) {
            restoredOutcomes.putAll(carriedOutcomes)
            restoredCarries.putAll(carriedCarries)

            // The transit role's ONLY seeding point. A transit frame reads a null [moveTarget] — hence a null
            // [plan] — so it lands here, on the plain-restore path; seeding it in the surgery below (which this
            // frame never reaches) or after the early return (which it never falls through) leaves the descend
            // set empty, and the rebuild then parks at the hosting RunStep instead of descending. Nothing fails:
            // the run looks migrated and the nested jump silently does not happen.
            seedDescendThrough(moveDescendCallSite)
            return state?.result
        }

        val documentPath = structure.scriptLocation.documentPath
        val dropStableIds = plan.dropSet.mapTo(HashSet()) {
            objectStableMapper.objectStableId(ObjectLocation(documentPath, it))
        }

        // A dropped step RE-RUNS, so any child invocation it hosted (a RunStep's sub-Script) is abandoned: its
        // capture must not be adopted by the fresh invocation the re-run launches. This is the same
        // invocation-identity signal a re-running loop sends via [dropReplay] (logic-spec §5) — and it is
        // required here for the same reason: [Execution.restored] is delivered on (stableId, callSite), both of
        // which a re-hosted child still matches, so without the discard the sub-Script adopts its pre-jump
        // outcomes, replay-short-circuits every step, and appears to re-run instantaneously while handing back
        // its stale values. Must precede the hosting, which it does — [restore] runs at [ScriptLogic.run] start.
        //
        // Deliberately scoped to the DROP set, not the skip set: a step the walk skips over never runs in the
        // rebuilt spine, so it never re-hosts and nothing can adopt its child's capture — that capture is simply
        // unclaimed, and the engine's orphan sweep disposes it at the next barrier like any other (see
        // [RunEngine.sweepOrphans]: "an orphaned detached resource lingers at most one edit cycle").
        discardCaptured(dropStableIds)

        for ((stableId, value) in carriedOutcomes) {
            if (stableId !in dropStableIds) {
                restoredOutcomes[stableId] = value
            }
        }
        for ((stableId, carry) in carriedCarries) {
            // A dropped loop restarts at iteration 0, so its stale cursor must not carry (else it would resume
            // mid-iteration instead of restarting).
            if (stableId !in dropStableIds) {
                restoredCarries[stableId] = carry
            }
        }

        for (ancestor in plan.ancestors) {
            descendSteps.add(objectStableMapper.objectStableId(ObjectLocation(documentPath, ancestor)))
        }
        for (preceding in plan.precedingOnPath) {
            val location = ObjectLocation(documentPath, preceding)
            val stableId = objectStableMapper.objectStableId(location)
            if (stableId in restoredOutcomes) {
                continue
            }

            // A step the jump walked past that was MID-FLIGHT may still have a value worth handing downstream
            // (a loop's collected iterations — see [ScriptStep.partialOutcome]). Committing it as a restored
            // outcome routes it through the spine's existing replay short-circuit: adopted, never re-run, Done,
            // and present in the value graph so a later reference resolves instead of error-parking.
            val partial = restoredCarries[stableId]?.let { structure.scriptStepAt(location).partialOutcome(it) }
            if (partial == null) {
                skippedSteps.add(stableId)
            }
            else {
                restoredOutcomes[stableId] = partial.value
                partialDetails[stableId] = partial.detail

                // It is adopted rather than re-run, so its cursor is spent — a stale one must not survive to be
                // consumed by a later backward jump back into the loop.
                restoredCarries.remove(stableId)
            }
        }

        // Reset the dropped steps' stale displays: the rebuilt spine parks at the target and never re-walks to
        // them, so their old Done / Error traces would otherwise linger. (Skipped steps get their Skipped trace
        // from the spine when the walk reaches them; the target repaints Running at its checkpoint.)
        for (stableId in dropStableIds) {
            emitIdle(stableId)
        }

        return state?.result
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Seed the descend set for a TRANSIT frame: the call-site it hosts the addressed frame from, plus the
    // containers enclosing it ([ScriptJumpAnalysis.descendAncestors]) — all run with their boundary suppressed,
    // so the paused rebuild reaches the hosting RunStep and descends instead of parking at it.
    //
    // A transit frame is by construction blocked in [Execution.host] at the barrier, so every step BEFORE the
    // call-site completed and replay-adopts: the rebuild cannot park short of the RunStep. The exception is an
    // ENCLOSING container — mid-flight, holding no outcome, hence re-run rather than adopted — which is
    // precisely why the ancestors join the set too. An If re-evaluates its condition; a LOOP re-enters at the
    // cursor [restoredCarries] carried and the iteration it resumes skips its own [dropReplay] reset, so the
    // call-site is reached inside the very iteration that was in flight, with that iteration's item bound.
    //
    // A claim left unconsumed is inert. Were the rebuilt loop to restart at iteration 0 instead of resuming
    // (its cursor did not survive the edit — see [survivesEdit]), the descent would ride that fresh iteration,
    // whose [dropReplay] discards the call-site's captures first; the re-hosted child then holds neither a
    // capture nor a descend obligation, so it never reads the move target — the jump is dropped, not misapplied.
    //
    // The id is resolved leniently and ignored when it is not this document's: the engine addresses frames
    // precisely, but a Logic handed a request it cannot place must ignore it (logic-spec §4) rather than throw.
    private fun seedDescendThrough(moveDescendCallSite: ObjectStableId?) {
        val callSiteLocation = moveDescendCallSite
            ?.let { objectStableMapper.objectLocationOrNull(it) }
            ?: return
        if (callSiteLocation.documentPath != structure.scriptLocation.documentPath) {
            return
        }

        val ancestors = ScriptJumpAnalysis.descendAncestors(
            structure.graphNotation, callSiteLocation.documentPath, structure.scriptTree,
            callSiteLocation.objectPath)
            ?: return

        descendSteps.add(objectStableMapper.objectStableId(callSiteLocation))
        for (ancestor in ancestors) {
            descendSteps.add(
                objectStableMapper.objectStableId(ObjectLocation(callSiteLocation.documentPath, ancestor)))
        }
    }


    // Whether the element that produced a carried entry is still the element this id names. The removal set is
    // the authority; the unresolvable-id check is the backstop for a removal no barrier reported.
    private fun survivesEdit(stableId: ObjectStableId, removedStableIds: Set<ObjectStableId>): Boolean {
        return stableId !in removedStableIds &&
                objectStableMapper.objectLocationOrNull(stableId) != null
    }


    // Resolve a move-to target stable id against the CURRENT structure to a valid [ScriptJumpAnalysis.ScriptJumpPlan],
    // or null when it does not resolve to a jumpable step in this Script's root document (the ignore-contract).
    private fun jumpPlanFor(moveTarget: ObjectStableId): ScriptJumpAnalysis.ScriptJumpPlan? {
        val targetLocation = objectStableMapper.objectLocationOrNull(moveTarget)
            ?: return null
        if (targetLocation.documentPath != structure.scriptLocation.documentPath) {
            return null
        }
        return ScriptJumpAnalysis
            .plan(structure.graphNotation, targetLocation.documentPath, structure.scriptTree, targetLocation.objectPath)
            .takeIf { it.valid }
    }
}
