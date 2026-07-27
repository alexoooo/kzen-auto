package tech.kzen.auto.server.objects.script.step.control.foreach

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ForEachProgress
import tech.kzen.auto.common.util.TraceDisplay
import tech.kzen.auto.server.objects.script.api.PartialOutcome
import tech.kzen.auto.server.objects.script.api.ScriptControlSignal
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassNames


@Reflect
class ForEachStep(
    private val items: ObjectLocation,
    steps: List<ObjectLocation>,
    private val selfLocation: ObjectLocation
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    private val bodySteps = steps


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The loop's mid-flight migration carry ([StepExecution.recordCarry]): the LIVE [iterator] — carried as-is,
     * like any other migrating in-memory handle, so resume continues the traversal exactly where it left off
     * for ANY [Iterable] with no re-iterability constraint — plus the in-flight [currentItem] (already consumed
     * from the iterator; the resumed iteration replays it), its [currentIndex], the [collectedOutputs] so far,
     * and the [totalSize] for the progress counter. Best-effort by design (logic-spec §5): the iterator belongs
     * to the pre-edit items value, so an edit to the items PRODUCER is not reflected until the loop next starts
     * — far better for interactive development than re-running completed iterations' side effects from scratch.
     *
     * [collectedOutputs] is likewise the LIVE list, not a per-iteration snapshot of it — the same "carry live
     * state as-is" rule the iterator follows, and O(1) per iteration rather than the O(n) copy that made a long
     * loop quadratic. Aliasing is sound because a migration barrier can only land on a `checkpoint`, which the
     * spine takes per body step: at every reachable capture point the live list holds exactly the completed
     * iterations, which is what a snapshot taken at this iteration's start would have held. Restore copies it
     * ([ArrayList] below), so the rebuilt run never mutates the carried list. [producedEntries] (the display
     * journal) is carried on the same terms, and [producedCount] with it because the journal is capped and so
     * cannot report the true total on its own.
     */
    private class LoopCursor(
        val iterator: Iterator<*>,
        val currentItem: Any?,
        val currentIndex: Int,
        val collectedOutputs: List<Any?>,
        val totalSize: Int?,
        val producedEntries: List<ForEachProgress.Entry>,
        val producedCount: Int
    )


    //-----------------------------------------------------------------------------------------------------------------
    // Loop over the iterable a predecessor produced, running the body once per element and collecting each
    // iteration's terminal value. The current element is bound under the loop's `item` binding so body
    // expressions can reference it.
    //
    // MID-LOOP MIGRATION RESUME (logic-spec §5): reaching here means the loop did NOT complete pre-edit (a
    // completed loop carries its own outcome and is short-circuited wholesale by the enclosing sequence). A
    // coroutine's loop position can't be re-pointed at the rebuilt body, so the loop re-enters from the top
    // and resumes from its restored [LoopCursor]: the live iterator continues the traversal, the in-flight
    // iteration replays with its carried item — its completed body prefix short-circuits to the frontier (its
    // outcomes are the only body entries in the restored capture; every other iteration begins with a
    // dropReplay iteration reset) — and the collected outputs are seeded. With no cursor (the loop never
    // started pre-edit) it simply runs fresh, the §5 safe default.
    override suspend fun run(execution: StepExecution): Any? {
        val itemBinding = ScriptConventions.orderedDirectChildLocations(
            execution.graphNotation, AttributeLocation(selfLocation, ScriptConventions.itemAttributePath))
            .singleOrNull()
            ?: error("ForEach step has no item binding: $selfLocation")

        val cursor = execution.restoredCarry(selfLocation) as? LoopCursor

        // Collect each iteration's value only if something reads this loop's own value. Nothing does when the
        // loop is (say) the Script's last root step — [ScriptLogic] discards the root sequence's value — and
        // collecting then costs a list that pins every iteration's terminal object for the run's lifetime. The
        // loop still returns a well-typed (empty) List, which is what its declared type promises and all any
        // in-scope expression step needs, since those resolve every in-scope value regardless of use.
        val collecting = execution.isValueReferenced(selfLocation)

        val iterator: Iterator<*>
        val output: ArrayList<Any?>
        val size: Int?
        var index: Int
        var item: Any?
        var replayInFlight: Boolean

        // The display journal (see [ForEachProgress]) and its untruncated total. Maintained regardless of
        // [collecting] — a loop nothing references still shows what it computed, at the cost of a bounded
        // string per iteration rather than a retained object graph.
        val journal: ArrayDeque<ForEachProgress.Entry>
        var producedCount: Int

        if (cursor != null) {
            iterator = cursor.iterator
            output = ArrayList(cursor.collectedOutputs)
            size = cursor.totalSize
            index = cursor.currentIndex
            item = cursor.currentItem
            journal = ArrayDeque(cursor.producedEntries)
            producedCount = cursor.producedCount
            replayInFlight = true
        }
        else {
            val iterable = execution.referencedValue(items) as? Iterable<*>
                ?: error("ForEach items are not iterable: $items")
            iterator = iterable.iterator()
            output = ArrayList()
            size = (iterable as? Collection<*>)?.size
            index = 0
            item = null
            journal = ArrayDeque()
            producedCount = 0
            replayInFlight = false
        }

        // The last iteration entered, for the progress re-emit on each exit path below. Null until the first
        // iteration starts, which is also the "an empty collection has no progress to show" case.
        var lastItemDisplay: String? = null
        var lastIndex = 0

        while (replayInFlight || iterator.hasNext()) {
            if (!replayInFlight) {
                item = iterator.next()

                // Iteration reset (see [StepExecution.dropReplay]) — skipped for a resumed in-flight
                // iteration, whose completed body prefix must stay replayable.
                execution.dropReplay(bodySteps)
            }

            // The cursor is (re-)recorded at each iteration's start, so a pause anywhere in the body migrates
            // with the live iterator and this iteration's item. Re-recording on the resumed run's first pass
            // matters too: its carry starts empty, so a SECOND edit must still capture the cursor.
            execution.recordCarry(
                selfLocation, LoopCursor(iterator, item, index, output, size, journal, producedCount))
            replayInFlight = false

            // Live loop progress on the ForEach card: the loop is the current step here, so the detail
            // attributes to it, and markDone carries the final iteration's detail into the Done trace. The
            // item is capped like any other trace display — it is built here rather than passed through the
            // spine's displayOf, so it bounds itself.
            val itemDisplay = TraceDisplay.truncatedToString(item, TraceDisplay.maxScriptTraceChars)
            lastItemDisplay = itemDisplay
            lastIndex = index
            traceProgress(execution, itemDisplay, index, size, journal, producedCount)

            execution.bind(itemBinding, item)
            val bodyValue = execution.runSteps(bodySteps)

            // Control flow (see [ScriptControlSignal]): Skip -> this iteration contributes nothing, continue;
            // Finish -> exit with the outputs collected so far; a signal targeting an OUTER loop or End Script ->
            // propagate (the enclosing spine short-circuits this loop as a pass-through). The cursor carry is
            // cleared on any signal exit so a stale cursor never migrates (same as normal completion).
            when (execution.consumeLoopSignal(selfLocation)) {
                is ScriptControlSignal.FinishLoop -> {
                    execution.recordCarry(selfLocation, null)
                    traceProgress(execution, itemDisplay, index, size, journal, producedCount)
                    return output
                }
                is ScriptControlSignal.SkipIteration ->
                    Unit  // contribute nothing for this iteration
                else -> {
                    if (execution.pendingControlSignal() != null) {
                        execution.recordCarry(selfLocation, null)
                        traceProgress(execution, itemDisplay, index, size, journal, producedCount)
                        return output
                    }
                    if (collecting) {
                        output.add(bodyValue)
                    }
                    // The journal entry is appended at exactly the point the value is collected, so the two
                    // stay index-for-index aligned — which is what lets a partial commit be read against it.
                    journal.append(item, bodyValue)
                    producedCount += 1
                }
            }
            index += 1
        }

        // Completed: the loop's own outcome carries; a stale cursor must not. The final iteration's value is
        // collected AFTER its start-of-iteration progress emit, so without this the Done trace's detail (which
        // markDone carries over from the last emit) would be one iteration behind.
        execution.recordCarry(selfLocation, null)
        lastItemDisplay?.let { traceProgress(execution, it, lastIndex, size, journal, producedCount) }
        return output
    }


    /**
     * The loop's value when a forward move-to (Set Next Statement) skips over it mid-flight: the iterations it
     * had already collected, so a step referencing this loop reads a short list rather than error-parking on an
     * absent value. Empty when the loop was not collecting — still a well-typed [List], which is all its declared
     * type promises. The committed detail flags itself [ForEachProgress.partial] so the card says so.
     */
    override fun partialOutcome(carry: Any): PartialOutcome? {
        val cursor = carry as? LoopCursor
            ?: return null

        val progress = ForEachProgress(
            TraceDisplay.truncatedToString(cursor.currentItem, TraceDisplay.maxScriptTraceChars),
            cursor.currentIndex,
            cursor.totalSize,
            cursor.producedEntries,
            cursor.producedCount,
            partial = true)

        return PartialOutcome(ArrayList(cursor.collectedOutputs), progress.asExecutionValue())
    }


    // The body is injected directly (no group nesting), so the notation is not needed here.
    override fun nestedStepLists(graphNotation: GraphNotation): List<List<ObjectLocation>> {
        return listOf(bodySteps)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun traceProgress(
        execution: StepExecution,
        itemDisplay: String,
        index: Int,
        size: Int?,
        journal: List<ForEachProgress.Entry>,
        producedCount: Int
    ) {
        execution.traceDetail(
            ForEachProgress(itemDisplay, index, size, journal.toList(), producedCount).asExecutionValue())
    }


    // Bounded append (see [ForEachProgress] retention): the journal keeps the most recent entries, so a
    // million-iteration loop costs a fixed amount of trace rather than growing one per iteration.
    private fun ArrayDeque<ForEachProgress.Entry>.append(item: Any?, value: Any?) {
        while (size >= ForEachProgress.maxProducedEntries) {
            removeFirst()
        }
        addLast(ForEachProgress.entryOf(item, value))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition? {
        // Output is a List of what the loop collects each iteration: the body's terminal step value,
        // mirroring IfStep whose type is its branches' terminal type. NOT the `items` element type — that's
        // the loop variable's type (ForEachItemBinding), which it reads straight from `items`. An empty body
        // has no value to collect, so its element type is unknown (Any). Defer (null) until the terminal step
        // is validated; ScriptValidator iterates to a fixpoint.
        val elementType = bodyTerminalType(scriptDefinitionContext)
            ?: return null

        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType(
                TypeMetadata(
                    ClassNames.kotlinList,
                    listOf(elementType),
                    false))))
    }


    // The element type the loop collects: its last body step's resolved type, Unit when that terminal
    // validated without a value, or Any for an empty body. Null means the terminal isn't validated yet —
    // the caller should defer.
    private fun bodyTerminalType(scriptDefinitionContext: ScriptDefinitionContext): TypeMetadata? {
        val terminal = bodySteps.lastOrNull()
            ?: return TypeMetadata.any

        val validation = scriptDefinitionContext.scriptValidation.stepValidations[terminal.objectPath]
            ?: return null

        return validation.typeMetadata ?: TypeMetadata.unit
    }
}
